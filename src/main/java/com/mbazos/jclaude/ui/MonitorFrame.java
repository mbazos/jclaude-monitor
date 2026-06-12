package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.config.AppConfig;
import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.model.WebUsageResult;
import com.mbazos.jclaude.service.ClaudeWebClient;
import com.mbazos.jclaude.service.DataPoller;
import com.mbazos.jclaude.util.Debug;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.SystemTray;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.BiConsumer;

import static com.mbazos.jclaude.ui.Theme.*;

public class MonitorFrame extends JFrame {

    private final WebUsagePanel webUsagePanel      = new WebUsagePanel();
    private final JLabel        lastSyncLabel      = new JLabel("Syncing…");
    private final JLabel        allTimeLabel       = new JLabel("Sessions: —  Messages: —");
    private final JLabel        settingsStatusLabel = new JLabel(" ", SwingConstants.LEFT);

    private JPanel          settingsPanel;
    private boolean         settingsVisible = false;
    private DataPoller      poller;
    private TrayIconManager trayManager;
    private WebUsageResult  lastResult = null;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public MonitorFrame() {
        super("jclaude-monitor");

        applyUiDefaults();
        buildUi();
        restoreWindowState();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                saveWindowState();
                if (poller != null) poller.shutdown();
                trayManager.dispose();
                System.exit(0);
            }

            @Override public void windowIconified(java.awt.event.WindowEvent e) {
                if (trayManager.isTrayActive() && AppConfig.loadMinimizeToTray()) {
                    SwingUtilities.invokeLater(() -> setVisible(false));
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public BiConsumer<Optional<LocalStats>, WebUsageResult> getResultHandler() { return this::onResult; }
    public void setPoller(DataPoller poller) { this.poller = poller; }
    public boolean isTrayActive() { return trayManager.isTrayActive(); }

    // -------------------------------------------------------------------------
    // Result handler (called on EDT by DataPoller)
    // -------------------------------------------------------------------------

    private void onResult(Optional<LocalStats> stats, WebUsageResult usage) {
        webUsagePanel.update(usage);
        trayManager.update(usage);

        // Auto-show window when session becomes unavailable while the window is hidden.
        // Only trigger on the first unavailable result after a working session (or on startup
        // if credentials were present but already invalid) — not on every 60-second poll.
        boolean wasAvailable   = lastResult instanceof WebUsageResult.Available;
        boolean nowUnavailable = usage   instanceof WebUsageResult.Unavailable;
        if (nowUnavailable && (lastResult == null || wasAvailable) && !isVisible()) {
            setVisible(true);
            setState(JFrame.NORMAL);
            toFront();
        }
        lastResult = usage;

        allTimeLabel.setText(stats
                .map(s -> String.format("Sessions: %,d  Messages: %,d",
                        s.totalSessions(), s.totalMessages()))
                .orElse("Sessions: —  Messages: —"));
        lastSyncLabel.setText("Last sync: " + TIME_FMT.format(Instant.now()));
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUi() {
        setMinimumSize(new Dimension(360, 300));
        getContentPane().setBackground(BG_DARK);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(webUsagePanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(BG_DARK);

        settingsPanel = buildSettingsPanel();
        settingsPanel.setVisible(false);
        bottom.add(settingsPanel);
        bottom.add(buildStatusBar());

        getContentPane().add(bottom, BorderLayout.SOUTH);

        trayManager = new TrayIconManager(this);
    }

    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        // Header: label + close button
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(BG_DARK);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel header = new JLabel("CLAUDE.AI SESSION");
        header.setFont(MONO_BOLD);
        header.setForeground(ACCENT);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(MONO_BOLD);
        closeBtn.setForeground(FG_SECONDARY);
        closeBtn.setBackground(BG_DARK);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> toggleSettings());

        headerRow.add(header,   BorderLayout.WEST);
        headerRow.add(closeBtn, BorderLayout.EAST);
        panel.add(headerRow);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));

        // Login button
        JPanel loginRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        loginRow.setBackground(BG_DARK);
        loginRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JButton loginBtn = new JButton("Login with Claude.ai…");
        loginBtn.setFont(MONO_BOLD);
        loginBtn.setBackground(ACCENT);
        loginBtn.setForeground(FG_PRIMARY);
        loginBtn.setFocusPainted(false);
        loginBtn.setBorder(BorderFactory.createLineBorder(ACCENT));
        loginBtn.addActionListener(e -> openBrowserLogin());
        loginRow.add(loginBtn);
        panel.add(loginRow);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        // Test & Save + Clear buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnRow.setBackground(BG_DARK);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JButton testBtn  = new JButton("Test & Save");
        JButton clearBtn = new JButton("Clear");
        styleButton(testBtn);
        styleButton(clearBtn);
        testBtn.addActionListener(e  -> runTest());
        clearBtn.addActionListener(e -> clearSession());
        btnRow.add(testBtn);
        btnRow.add(clearBtn);
        panel.add(btnRow);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Status label
        settingsStatusLabel.setFont(MONO_SMALL);
        settingsStatusLabel.setForeground(FG_SECONDARY);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusRow.setBackground(BG_DARK);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        statusRow.add(settingsStatusLabel);
        panel.add(statusRow);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        // Always on Top checkbox
        JCheckBox alwaysOnTopCheck = new JCheckBox("Always on Top");
        alwaysOnTopCheck.setFont(MONO_SMALL);
        alwaysOnTopCheck.setBackground(BG_DARK);
        alwaysOnTopCheck.setForeground(FG_SECONDARY);
        alwaysOnTopCheck.setFocusPainted(false);
        alwaysOnTopCheck.setSelected(AppConfig.loadAlwaysOnTop());
        alwaysOnTopCheck.addActionListener(e -> {
            setAlwaysOnTop(alwaysOnTopCheck.isSelected());
            saveWindowState();
        });
        JPanel checkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        checkRow.setBackground(BG_DARK);
        checkRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        checkRow.add(alwaysOnTopCheck);
        panel.add(checkRow);

        if (SystemTray.isSupported()) {
            JCheckBox minimizeToTrayCheck = new JCheckBox("Minimize to Tray");
            minimizeToTrayCheck.setFont(MONO_SMALL);
            minimizeToTrayCheck.setBackground(BG_DARK);
            minimizeToTrayCheck.setForeground(FG_SECONDARY);
            minimizeToTrayCheck.setFocusPainted(false);
            minimizeToTrayCheck.setSelected(AppConfig.loadMinimizeToTray());
            minimizeToTrayCheck.addActionListener(e -> {
                try {
                    AppConfig.saveMinimizeToTray(minimizeToTrayCheck.isSelected());
                } catch (Exception ex) {
                    Debug.warn("jclaude-monitor", "Failed to save minimize-to-tray: " + ex.getMessage());
                }
            });
            JPanel minimizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            minimizeRow.setBackground(BG_DARK);
            minimizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            minimizeRow.add(minimizeToTrayCheck);
            panel.add(minimizeRow);
        }

        return panel;
    }

    private JPanel buildStatusBar() {
        lastSyncLabel.setFont(MONO_SMALL);
        lastSyncLabel.setForeground(FG_SECONDARY);
        allTimeLabel.setFont(MONO_SMALL);
        allTimeLabel.setForeground(FG_SECONDARY);

        JButton settingsBtn = new JButton("⚙");
        settingsBtn.setFont(MONO_PLAIN);
        settingsBtn.setForeground(FG_SECONDARY);
        settingsBtn.setBackground(BG_DARK);
        settingsBtn.setContentAreaFilled(false);
        settingsBtn.setBorderPainted(false);
        settingsBtn.setFocusPainted(false);
        settingsBtn.setToolTipText("Settings");
        settingsBtn.addActionListener(e -> toggleSettings());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        left.setBackground(BG_DARK);
        left.add(lastSyncLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        right.setBackground(BG_DARK);
        right.add(allTimeLabel);
        right.add(settingsBtn);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_DARK);
        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // -------------------------------------------------------------------------
    // Settings toggle
    // -------------------------------------------------------------------------

    private void toggleSettings() {
        int panelH = settingsPanel.getPreferredSize().height;
        settingsVisible = !settingsVisible;
        settingsPanel.setVisible(settingsVisible);
        int newH = settingsVisible
                ? getHeight() + panelH
                : Math.max(getHeight() - panelH, getMinimumSize().height);
        setSize(getWidth(), newH);
        revalidate();
    }

    // -------------------------------------------------------------------------
    // Session actions
    // -------------------------------------------------------------------------

    private void openBrowserLogin() {
        BrowserLoginDialog browser = new BrowserLoginDialog(this, (sk, orgId) -> {
            settingsStatusLabel.setForeground(FG_SECONDARY);
            settingsStatusLabel.setText("Testing connection…");
            runTestInBackground(sk, orgId);
        });
        browser.setVisible(true);
    }

    private void runTest() {
        String sk    = AppConfig.loadSessionKey();
        String orgId = AppConfig.loadSessionOrgId();
        if (sk == null || sk.isBlank() || orgId == null || orgId.isBlank()) {
            settingsStatusLabel.setForeground(RED);
            settingsStatusLabel.setText("No credentials — login first.");
            return;
        }
        settingsStatusLabel.setForeground(FG_SECONDARY);
        settingsStatusLabel.setText("Testing…");
        runTestInBackground(sk, orgId);
    }

    private void runTestInBackground(String sk, String orgId) {
        Thread t = new Thread(() -> {
            String outcome;
            boolean success;
            ClaudeWebClient client = new ClaudeWebClient(sk, orgId);
            try {
                WebUsageResult result = client.fetch();
                success = result instanceof WebUsageResult.Available;
                outcome = switch (result) {
                    case WebUsageResult.Available a -> {
                        if (a.fiveHourUtil() != null && a.sevenDayUtil() != null) {
                            yield String.format("Connected — 5h: %.1f%%, 7d: %.1f%%",
                                    a.fiveHourUtil(), a.sevenDayUtil());
                        } else if (a.extraUsageEnabled()) {
                            yield String.format("Connected — Budget: %.1f%% used",
                                    a.extraUsageUtil());
                        } else {
                            yield "Connected";
                        }
                    }
                    case WebUsageResult.Unavailable u -> u.reason();
                };
                if (success) {
                    AppConfig.saveSessionKey(sk);
                    AppConfig.saveSessionOrgId(orgId);
                }
            } catch (Exception ex) {
                success  = false;
                outcome  = "Error: " + ex.getMessage();
            }
            final String msg        = outcome;
            final boolean ok        = success;
            final ClaudeWebClient c = ok ? client : null;
            SwingUtilities.invokeLater(() -> {
                settingsStatusLabel.setForeground(ok ? GREEN : RED);
                settingsStatusLabel.setText(ok ? "Saved. " + msg : msg);
                if (ok && poller != null) {
                    poller.updateWebClient(c);
                    poller.refreshNow();
                    if (trayManager.isTrayActive()) {
                        setVisible(false);
                    }
                }
            });
        }, "session-test");
        t.setDaemon(true);
        t.start();
    }

    private void clearSession() {
        try {
            AppConfig.clearSessionKey();
            AppConfig.clearSessionOrgId();
            settingsStatusLabel.setForeground(FG_SECONDARY);
            settingsStatusLabel.setText("Session cleared.");
            if (poller != null) poller.updateWebClient(null);
        } catch (Exception ex) {
            settingsStatusLabel.setForeground(RED);
            settingsStatusLabel.setText("Clear failed: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Window state
    // -------------------------------------------------------------------------

    private void restoreWindowState() {
        int[] state = AppConfig.loadWindowState();
        if (state != null) {
            setBounds(state[0], state[1], state[2], state[3]);
        } else {
            setSize(380, 300);
            setLocationRelativeTo(null);
        }
        setAlwaysOnTop(AppConfig.loadAlwaysOnTop());
    }

    private void saveWindowState() {
        try {
            AppConfig.saveWindowState(getX(), getY(), getWidth(), getHeight(), isAlwaysOnTop());
        } catch (Exception ex) {
            Debug.warn("jclaude-monitor", "Failed to save window state: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void applyUiDefaults() {
        UIManager.put("Panel.background",      BG_DARK);
        UIManager.put("Label.foreground",      FG_PRIMARY);
        UIManager.put("Label.font",            MONO_PLAIN);
        UIManager.put("Button.font",           MONO_PLAIN);
        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("Viewport.background",   BG_DARK);
    }

    private static void styleButton(JButton button) {
        button.setFont(MONO_PLAIN);
        button.setBackground(BG_SECTION);
        button.setForeground(FG_PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(BORDER));
    }
}
