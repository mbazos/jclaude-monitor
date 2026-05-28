package com.mbazos.jclaude;

import com.mbazos.jclaude.config.AppConfig;
import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.model.PollResult;
import com.mbazos.jclaude.service.AnthropicApiClient;
import com.mbazos.jclaude.service.DataPoller;
import com.mbazos.jclaude.service.LocalDataReader;
import com.mbazos.jclaude.ui.MonitorFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.function.BiConsumer;

/**
 * Application entry point for jclaude-monitor.
 * <p>
 * Bootstraps the config, services, and UI on the Swing Event Dispatch Thread.
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 1. Ensure config directory exists
                AppConfig.ensureConfigDir();

                // 2. Load API key (null = LOCAL_ONLY mode)
                String apiKey = null;
                try {
                    apiKey = AppConfig.loadApiKey();
                } catch (Exception e) {
                    System.err.println("[jclaude-monitor] Failed to load API key: " + e.getMessage());
                }

                // 3. Create services
                AnthropicApiClient apiClient = apiKey != null ? new AnthropicApiClient(apiKey) : null;
                LocalDataReader localReader = new LocalDataReader();

                // 4. Create the frame first (to get the result handler)
                MonitorFrame frame = new MonitorFrame();

                // 5. Create poller with the frame's result handler
                double budget = AppConfig.loadBudget();
                BiConsumer<LocalStats, PollResult> handler = frame.getResultHandler();
                DataPoller poller = new DataPoller(apiClient, localReader, handler);
                poller.setBudget(budget);

                // 6. Wire poller into frame (for Refresh button and shutdown)
                frame.setPoller(poller);

                // 7. Show frame and start polling
                frame.setVisible(true);
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
