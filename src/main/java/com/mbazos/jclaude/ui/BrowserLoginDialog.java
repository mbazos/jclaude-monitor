package com.mbazos.jclaude.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static com.mbazos.jclaude.ui.Theme.*;

public class BrowserLoginDialog extends JDialog {

    private static final String LOGIN_URL = "https://claude.ai/login";

    private final JLabel         statusLabel   = new JLabel("Loading claude.ai…", SwingConstants.LEFT);
    private final JButton        cancelButton  = new JButton("Cancel");
    private final BiConsumer<String, String> onCredentials;
    private final AtomicBoolean  extracting    = new AtomicBoolean(false);
    private final CookieManager  cookieManager;
    private JsBridge jsBridge; // field reference prevents GC while JS holds it

    public BrowserLoginDialog(Frame parent, BiConsumer<String, String> onCredentials) {
        super(parent, "Login to Claude.ai", true);
        this.onCredentials = onCredentials;

        // Install CookieManager BEFORE the WebView is created.
        // When JavaFX WebEngine runs from the classpath (fat-JAR, non-modular), it uses
        // Java's URLConnection for HTTP, so this handler captures ALL cookies including HttpOnly.
        cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        buildUi();
        setSize(920, 700);
        setMinimumSize(new Dimension(700, 500));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUi() {
        getContentPane().setBackground(BG_DARK);
        getContentPane().setLayout(new BorderLayout());

        JFXPanel jfxPanel = new JFXPanel();
        getContentPane().add(jfxPanel, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBackground(BG_DARK);
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        statusLabel.setFont(MONO_SMALL);
        statusLabel.setForeground(FG_SECONDARY);
        cancelButton.setFont(MONO_PLAIN);
        cancelButton.setBackground(BG_SECTION);
        cancelButton.setForeground(FG_PRIMARY);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorder(BorderFactory.createLineBorder(BORDER));
        cancelButton.addActionListener(e -> dispose());
        statusBar.add(statusLabel, BorderLayout.CENTER);
        statusBar.add(cancelButton, BorderLayout.EAST);
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        Platform.setImplicitExit(false);
        Platform.runLater(() -> initWebView(jfxPanel));
    }

    // -------------------------------------------------------------------------
    // JavaFX WebView (runs on JavaFX Application Thread)
    // -------------------------------------------------------------------------

    private void initWebView(JFXPanel jfxPanel) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        jfxPanel.setScene(new Scene(webView));

        // Primary trigger: URL changes fire for both HTTP redirects and SPA navigation.
        // We do NOT gate on stateProperty == SUCCEEDED because claude.ai keeps long-lived
        // WebSocket/SSE connections open, which prevents the page from ever leaving RUNNING.
        engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (newUrl != null && !newUrl.equals(oldUrl)) {
                handleUrlChange(engine, newUrl);
            }
        });

        // Secondary: update status for full page load events.
        // Skip "Loading…" once extraction has started so we don't overwrite the message.
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.RUNNING) {
                if (!extracting.get()) updateStatus("Loading…");
            } else if (newState == Worker.State.SUCCEEDED) {
                handleUrlChange(engine, engine.getLocation());
            } else if (newState == Worker.State.FAILED) {
                if (!extracting.get()) updateStatus("Page failed to load.");
                extracting.set(false);
            }
        });

        engine.load(LOGIN_URL);
    }

    private void handleUrlChange(WebEngine engine, String url) {
        if (url == null) return;
        if (isLoginOrSignup(url)) {
            updateStatus("Log in to your Claude account…");
        } else if (isClaudeHome(url) && extracting.compareAndSet(false, true)) {
            updateStatus("Extracting credentials…");
            // Wait for the login response's Set-Cookie header to be committed to the store
            PauseTransition wait = new PauseTransition(Duration.millis(1200));
            wait.setOnFinished(e -> extractCredentials(engine));
            wait.play();
        }
    }

    private static boolean isLoginOrSignup(String url) {
        try {
            String path = new URI(url).getPath();
            if (path == null) return false;
            return path.equals("/login")     || path.startsWith("/login/")
                || path.equals("/signup")    || path.startsWith("/signup/")
                || path.startsWith("/verify")
                || path.startsWith("/magic-link");
        } catch (URISyntaxException e) {
            return url.contains("/login") || url.contains("/signup");
        }
    }

    private static boolean isClaudeHome(String url) {
        return url.startsWith("https://claude.ai/") && !isLoginOrSignup(url);
    }

    // -------------------------------------------------------------------------
    // Credential extraction
    // -------------------------------------------------------------------------

    @SuppressWarnings("removal")
    private void extractCredentials(WebEngine engine) {
        jsBridge = new JsBridge();

        // Primary path: read sessionKey from Java's CookieManager.
        // This works for HttpOnly cookies when WebEngine uses Java's networking (classpath mode).
        String skFromJava = findSessionKeyInCookieStore();
        if (skFromJava != null) {
            jsBridge.sessionKeyFromJava = skFromJava;
        }

        JSObject window = (JSObject) engine.executeScript("window");
        window.setMember("_jcBridge", jsBridge);

        // JS: get session key from Java bridge (HttpOnly-safe) or fall back to document.cookie.
        // Then fetch the first org UUID and report both back.
        engine.executeScript("""
            (async function() {
                try {
                    // Try the Java-captured session key first (works even if cookie is HttpOnly)
                    var sk = window._jcBridge.getSessionKeyFromJava() || '';

                    // Fallback: document.cookie (non-HttpOnly cookies only)
                    if (!sk) {
                        document.cookie.split(';').forEach(function(c) {
                            var idx = c.indexOf('=');
                            if (idx > 0 && c.substring(0, idx).trim() === 'sessionKey') {
                                sk = c.substring(idx + 1).trim();
                            }
                        });
                    }

                    if (!sk) {
                        window._jcBridge.onError(
                            'Session key not captured — close and use manual entry instead.');
                        return;
                    }

                    var resp = await fetch('/api/organizations');
                    if (!resp.ok) {
                        window._jcBridge.onError(
                            'Failed to load organizations: HTTP ' + resp.status);
                        return;
                    }
                    var orgs = await resp.json();
                    var orgId = Array.isArray(orgs) && orgs.length > 0 ? (orgs[0].uuid || '') : '';
                    if (!orgId) {
                        window._jcBridge.onError('No organization found in your account.');
                        return;
                    }

                    window._jcBridge.onSuccess(sk, orgId);
                } catch (e) {
                    window._jcBridge.onError(e.toString());
                }
            })();
            """);
    }

    private String findSessionKeyInCookieStore() {
        for (String domain : new String[]{"https://claude.ai", "https://www.claude.ai"}) {
            try {
                for (HttpCookie c : cookieManager.getCookieStore().get(URI.create(domain))) {
                    if ("sessionKey".equals(c.getName())) return c.getValue();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Java object callable from JavaScript
    // -------------------------------------------------------------------------

    public class JsBridge {
        String sessionKeyFromJava; // set when CookieManager captured the session key

        public String getSessionKeyFromJava() {
            return sessionKeyFromJava != null ? sessionKeyFromJava : "";
        }

        public void onSuccess(String sessionKey, String orgId) {
            SwingUtilities.invokeLater(() -> {
                onCredentials.accept(sessionKey, orgId);
                dispose();
            });
        }

        public void onError(String message) {
            extracting.set(false);
            SwingUtilities.invokeLater(() -> {
                statusLabel.setForeground(RED);
                statusLabel.setText(message);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setForeground(FG_SECONDARY);
            statusLabel.setText(message);
        });
    }
}
