package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.config.AppConfig;
import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.model.PollResult;
import com.mbazos.jclaude.service.AnthropicApiClient;
import com.mbazos.jclaude.service.DataPoller;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * The main application window.
 * <p>
 * Construction order (required by {@link DataPoller} needing a handler in its constructor):
 * <ol>
 *   <li>Create {@code MonitorFrame} — builds the shell UI and exposes its result handler.</li>
 *   <li>Obtain the handler via {@link #getResultHandler()}.</li>
 *   <li>Construct {@link DataPoller} with that handler.</li>
 *   <li>Call {@link #setPoller(DataPoller)} to wire up the Refresh button.</li>
 *   <li>Call {@code poller.start()} (on any thread — it schedules internally).</li>
 * </ol>
 */
public class MonitorFrame extends JFrame {

    private final QuotaSectionPanel quotaPanel    = new QuotaSectionPanel();
    private final SessionsPanel     sessionsPanel = new SessionsPanel();
    private final StatsPanel        statsPanel    = new StatsPanel();
    private final JLabel            lastSyncLabel = new JLabel("Syncing…");
    private final JButton           refreshButton = new JButton("↺ Refresh");

    /** Wired up after DataPoller is constructed. */
    private DataPoller poller;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public MonitorFrame() {
        super("jclaude-monitor");

        // Apply global UI defaults before creating any components
        applyUiDefaults();

        buildUi();
        buildMenuBar();
        restoreWindowState();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveWindowState();
                if (poller != null) poller.shutdown();
                System.exit(0);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the result handler that must be passed to the {@link DataPoller} constructor.
     * The handler is guaranteed to be called on the EDT (DataPoller uses invokeLater).
     */
    public BiConsumer<LocalStats, PollResult> getResultHandler() {
        return this::onResult;
    }

    /**
     * Wires the DataPoller into the frame so the Refresh button can trigger immediate polls.
     */
    public void setPoller(DataPoller poller) {
        this.poller = poller;
    }

    // -------------------------------------------------------------------------
    // Result handler (called on EDT by DataPoller)
    // -------------------------------------------------------------------------

    private void onResult(LocalStats stats, PollResult result) {
        // stats can be null if a local-read error occurred
        sessionsPanel.update(stats);
        statsPanel.update(stats);
        quotaPanel.update(result);
        lastSyncLabel.setText("Last sync: " + TIME_FMT.format(Instant.now()));
        refreshButton.setEnabled(true);
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUi() {
        setMinimumSize(new Dimension(380, 480));
        getContentPane().setBackground(BG_DARK);

        // -- NORTH: title bar --------------------------------------------------
        JLabel titleLabel = new JLabel("● jclaude-monitor");
        titleLabel.setFont(MONO_LARGE);
        titleLabel.setForeground(ACCENT);

        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        northPanel.setBackground(BG_DARK);
        northPanel.add(titleLabel);

        // -- CENTER: scrollable content column ---------------------------------
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(BG_DARK);

        // API Quota section
        contentPanel.add(sectionHeaderLabel("API QUOTA"));
        contentPanel.add(quotaPanel);
        contentPanel.add(separator());

        // Sessions section
        contentPanel.add(sectionHeaderLabel("SESSIONS"));
        contentPanel.add(sessionsPanel);
        contentPanel.add(separator());

        // Stats section
        contentPanel.add(sectionHeaderLabel("STATS"));
        contentPanel.add(statsPanel);

        // Push content to the top, keep a filler at the bottom
        contentPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(BG_DARK);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // -- SOUTH: status bar -------------------------------------------------
        lastSyncLabel.setFont(MONO_SMALL);
        lastSyncLabel.setForeground(FG_SECONDARY);

        refreshButton.setFont(MONO_PLAIN);
        refreshButton.setBackground(BG_SECTION);
        refreshButton.setForeground(FG_PRIMARY);
        refreshButton.setFocusPainted(false);
        refreshButton.setBorder(BorderFactory.createLineBorder(BORDER));
        refreshButton.addActionListener(e -> {
            if (poller != null) {
                refreshButton.setEnabled(false);
                poller.refreshNow();
            }
        });

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        southPanel.setBackground(BG_DARK);
        southPanel.add(lastSyncLabel);
        southPanel.add(refreshButton);

        // -- Assemble ----------------------------------------------------------
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(northPanel,  BorderLayout.NORTH);
        getContentPane().add(scrollPane,  BorderLayout.CENTER);
        getContentPane().add(southPanel,  BorderLayout.SOUTH);
    }

    private void buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(BG_DARK);
        menuBar.setBorder(BorderFactory.createLineBorder(BORDER));

        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.setFont(MONO_PLAIN);
        settingsMenu.setForeground(FG_PRIMARY);

        // Always on Top
        JCheckBoxMenuItem alwaysOnTopItem = new JCheckBoxMenuItem("Always on Top");
        alwaysOnTopItem.setFont(MONO_PLAIN);
        alwaysOnTopItem.setSelected(AppConfig.loadAlwaysOnTop());
        setAlwaysOnTop(alwaysOnTopItem.isSelected());
        alwaysOnTopItem.addActionListener(e -> {
            boolean selected = alwaysOnTopItem.isSelected();
            setAlwaysOnTop(selected);
            saveWindowState(); // also persists alwaysOnTop flag
        });

        // API Key
        JMenuItem apiKeyItem = new JMenuItem("API Key…");
        apiKeyItem.setFont(MONO_PLAIN);
        apiKeyItem.addActionListener(e -> openApiKeyDialog());

        // Monthly Budget
        JMenuItem budgetItem = new JMenuItem("Monthly Budget…");
        budgetItem.setFont(MONO_PLAIN);
        budgetItem.addActionListener(e -> openBudgetDialog());

        // About
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.setFont(MONO_PLAIN);
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(
                this,
                "jclaude-monitor v1.0.0\nClaude Code usage monitor",
                "About jclaude-monitor",
                JOptionPane.INFORMATION_MESSAGE
        ));

        settingsMenu.add(alwaysOnTopItem);
        settingsMenu.addSeparator();
        settingsMenu.add(apiKeyItem);
        settingsMenu.add(budgetItem);
        settingsMenu.addSeparator();
        settingsMenu.add(aboutItem);

        menuBar.add(settingsMenu);
        setJMenuBar(menuBar);
    }

    // -------------------------------------------------------------------------
    // Menu actions
    // -------------------------------------------------------------------------

    private void openApiKeyDialog() {
        String currentKey = null;
        try {
            currentKey = AppConfig.loadApiKey();
        } catch (Exception ex) {
            System.err.println("[jclaude-monitor] Could not load API key: " + ex.getMessage());
        }

        SetupDialog dialog = new SetupDialog(this, currentKey);
        dialog.setVisible(true);  // blocks until closed (modal)

        String newKey = dialog.getApiKey();
        if (poller != null) {
            AnthropicApiClient newClient = (newKey != null && !newKey.isBlank())
                    ? new AnthropicApiClient(newKey)
                    : null;
            poller.updateApiClient(newClient);

            if (newClient == null) {
                quotaPanel.showNone();
            } else {
                // Trigger an immediate refresh with the new key
                refreshButton.setEnabled(false);
                poller.refreshNow();
            }
        }
    }

    private void openBudgetDialog() {
        String current = String.format("%.2f", AppConfig.loadBudget());
        String input = JOptionPane.showInputDialog(
                this,
                "Monthly budget in USD (0 = no limit):",
                current
        );
        if (input == null) return; // cancelled

        try {
            double budget = Double.parseDouble(input.trim());
            AppConfig.saveBudget(budget);
            if (poller != null) {
                poller.setBudget(budget);
                refreshButton.setEnabled(false);
                poller.refreshNow();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid number: " + input,
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save budget: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Window state persistence
    // -------------------------------------------------------------------------

    private void restoreWindowState() {
        int[] state = AppConfig.loadWindowState();
        if (state != null) {
            setBounds(state[0], state[1], state[2], state[3]);
        } else {
            setSize(420, 580);
            setLocationRelativeTo(null); // centre on screen
        }
        setAlwaysOnTop(AppConfig.loadAlwaysOnTop());
    }

    private void saveWindowState() {
        try {
            AppConfig.saveWindowState(
                    getX(), getY(), getWidth(), getHeight(),
                    isAlwaysOnTop()
            );
        } catch (Exception ex) {
            System.err.println("[jclaude-monitor] Failed to save window state: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void applyUiDefaults() {
        UIManager.put("Panel.background",  BG_DARK);
        UIManager.put("Label.foreground",  FG_PRIMARY);
        UIManager.put("Label.font",        MONO_PLAIN);
        UIManager.put("Button.font",       MONO_PLAIN);
        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("Viewport.background",   BG_DARK);
    }

    private static JLabel sectionHeaderLabel(String text) {
        JLabel label = new JLabel(" " + text);
        label.setFont(MONO_BOLD);
        label.setForeground(ACCENT);
        return label;
    }

    private static JSeparator separator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER);
        sep.setBackground(BG_DARK);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }
}
