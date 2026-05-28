package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.config.AppConfig;
import com.mbazos.jclaude.model.QuotaResult;
import com.mbazos.jclaude.service.AnthropicApiClient;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.Map;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Modal dialog for configuring the Anthropic API key.
 * <p>
 * The "Test" button fires a background probe (not on the EDT) and
 * reports the detected plan type.  "Save" validates and persists the key.
 * "Skip / Remove Key" clears any stored key and closes.
 */
public class SetupDialog extends JDialog {

    private final JPasswordField keyField    = new JPasswordField(30);
    private final JButton        testButton  = new JButton("Test");
    private final JButton        saveButton  = new JButton("Save");
    private final JButton        skipButton  = new JButton("Skip / Remove Key");
    private final JLabel         statusLabel = new JLabel(" ", SwingConstants.LEFT);

    /** The raw key returned to the caller; null if skipped/cancelled. */
    private String resultKey = null;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public SetupDialog(Frame parent, String currentKey) {
        super(parent, "API Key Configuration", true);

        // Pre-fill field if a key is already stored
        if (currentKey != null && !currentKey.isBlank()) {
            keyField.setText(currentKey);
        }

        buildUi();
        wireActions();

        pack();
        setMinimumSize(new Dimension(420, 200));
        setLocationRelativeTo(parent);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the new raw API key entered by the user, or {@code null} if the
     * user chose to skip / remove the key.
     */
    public String getApiKey() {
        return resultKey;
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUi() {
        getContentPane().setBackground(BG_DARK);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Key entry row
        JLabel keyLabel = new JLabel("API Key (sk-ant-…):");
        keyLabel.setFont(MONO_PLAIN);
        keyLabel.setForeground(FG_PRIMARY);

        keyField.setFont(MONO_PLAIN);
        keyField.setBackground(BG_SECTION);
        keyField.setForeground(FG_PRIMARY);
        keyField.setCaretColor(FG_PRIMARY);
        keyField.setBorder(BorderFactory.createLineBorder(BORDER));

        JPanel keyRow = new JPanel(new BorderLayout(6, 0));
        keyRow.setBackground(BG_DARK);
        keyRow.add(keyLabel, BorderLayout.WEST);
        keyRow.add(keyField, BorderLayout.CENTER);

        // Status label
        statusLabel.setFont(MONO_SMALL);
        statusLabel.setForeground(FG_SECONDARY);

        // Centre section
        JPanel centre = new JPanel();
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBackground(BG_DARK);
        centre.add(keyRow);
        centre.add(Box.createRigidArea(new Dimension(0, 6)));
        centre.add(statusLabel);

        // Button row
        styleButton(testButton);
        styleButton(saveButton);
        styleButton(skipButton);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonRow.setBackground(BG_DARK);
        buttonRow.add(testButton);
        buttonRow.add(saveButton);
        buttonRow.add(skipButton);

        root.add(centre,    BorderLayout.CENTER);
        root.add(buttonRow, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void wireActions() {
        testButton.addActionListener(e -> runTest());

        saveButton.addActionListener(e -> {
            String raw = new String(keyField.getPassword()).trim();
            if (!raw.startsWith("sk-ant-")) {
                statusLabel.setForeground(RED);
                statusLabel.setText("Key must start with sk-ant-");
                return;
            }
            try {
                AppConfig.saveApiKey(raw);
                resultKey = raw;
                dispose();
            } catch (Exception ex) {
                statusLabel.setForeground(RED);
                statusLabel.setText("Save failed: " + ex.getMessage());
            }
        });

        skipButton.addActionListener(e -> {
            try {
                AppConfig.clearApiKey();
            } catch (Exception ex) {
                // Best-effort clear; log but continue
                System.err.println("[jclaude-monitor] clearApiKey failed: " + ex.getMessage());
            }
            resultKey = null;
            dispose();
        });
    }

    // -------------------------------------------------------------------------
    // Background test
    // -------------------------------------------------------------------------

    private void runTest() {
        String raw = new String(keyField.getPassword()).trim();
        if (raw.isBlank()) {
            statusLabel.setForeground(RED);
            statusLabel.setText("Enter an API key first.");
            return;
        }

        testButton.setEnabled(false);
        statusLabel.setForeground(FG_SECONDARY);
        statusLabel.setText("Testing…");

        // Run the probe on a background thread — never the EDT
        Thread probeThread = new Thread(() -> {
            String outcome;
            boolean success;
            try {
                AnthropicApiClient client = new AnthropicApiClient(raw);
                QuotaResult result = client.probe(Map.of(), 0.0);
                outcome = describeResult(result);
                success = !(result instanceof QuotaResult.Unavailable);
            } catch (Exception ex) {
                outcome = "✗ Error: " + ex.getMessage();
                success = false;
            }

            final String finalOutcome  = outcome;
            final boolean finalSuccess = success;
            SwingUtilities.invokeLater(() -> {
                statusLabel.setForeground(finalSuccess ? GREEN : RED);
                statusLabel.setText(finalOutcome);
                testButton.setEnabled(true);
            });
        }, "api-key-test");
        probeThread.setDaemon(true);
        probeThread.start();
    }

    private static String describeResult(QuotaResult result) {
        return switch (result) {
            case QuotaResult.Standard ignored      -> "✓ Standard plan detected";
            case QuotaResult.EnterpriseTokens ignored -> "✓ Enterprise (token quota) plan detected";
            case QuotaResult.EnterpriseBudget ignored -> "✓ Enterprise (budget) plan detected";
            case QuotaResult.Unavailable u         -> "✗ " + u.reason();
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void styleButton(JButton button) {
        button.setFont(MONO_PLAIN);
        button.setBackground(BG_SECTION);
        button.setForeground(FG_PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(BORDER));
    }
}
