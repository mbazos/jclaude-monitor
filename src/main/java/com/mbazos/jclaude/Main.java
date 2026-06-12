package com.mbazos.jclaude;

import com.mbazos.jclaude.config.AppConfig;
import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.model.WebUsageResult;
import com.mbazos.jclaude.service.ClaudeWebClient;
import com.mbazos.jclaude.service.DataPoller;
import com.mbazos.jclaude.service.LocalDataReader;
import com.mbazos.jclaude.ui.MonitorFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Application entry point for jclaude-monitor.
 */
public class Main {

    public static void main(String[] args) {
        // On macOS: mark as accessory (menu-bar) app before AWT initialises so the
        // JVM process never gets a permanent Dock icon.  Must be set before the first
        // AWT call (SwingUtilities.invokeLater triggers toolkit init).
        System.setProperty("apple.awt.UIElement", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                AppConfig.ensureConfigDir();

                ClaudeWebClient webClient = null;
                String sessionKey = AppConfig.loadSessionKey();
                String orgId      = AppConfig.loadSessionOrgId();
                if (sessionKey != null && orgId != null) {
                    webClient = new ClaudeWebClient(sessionKey, orgId);
                }

                LocalDataReader localReader = new LocalDataReader();
                MonitorFrame frame = new MonitorFrame();

                BiConsumer<Optional<LocalStats>, WebUsageResult> handler = frame.getResultHandler();
                DataPoller poller = new DataPoller(webClient, localReader, handler);

                frame.setPoller(poller);
                // Start hidden when a session is configured and the tray icon is available;
                // the first poll will auto-show the window if the session turns out to be invalid.
                if (webClient == null || !frame.isTrayActive()) {
                    frame.setVisible(true);
                }
                poller.start();

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Failed to start jclaude-monitor:\n" + e.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
