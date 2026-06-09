package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.WebUsageResult;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.time.Duration;
import java.time.Instant;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Displays 5-hour and 7-day claude.ai usage obtained from the web API.
 * Uses {@link CardLayout} to switch between an "available" card (bars + reset
 * labels) and an "unavailable" card (muted reason label).
 */
public class WebUsagePanel extends JPanel {

    private static final String CARD_AVAILABLE   = "available";
    private static final String CARD_UNAVAILABLE = "unavailable";

    private final CardLayout cardLayout = new CardLayout();

    // Available card widgets
    private final ClaudeProgressBar fiveHourBar   = new ClaudeProgressBar();
    private final JLabel             fiveHourReset = new JLabel();
    private final ClaudeProgressBar sevenDayBar   = new ClaudeProgressBar();
    private final JLabel             sevenDayReset = new JLabel();

    // Unavailable card
    private final JLabel unavailableLabel = new JLabel();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public WebUsagePanel() {
        setLayout(cardLayout);
        setBackground(BG_SECTION);

        add(buildAvailableCard(), CARD_AVAILABLE);
        add(buildUnavailableCard(), CARD_UNAVAILABLE);

        // Default to "no session configured" state
        unavailableLabel.setText(" No session key — Settings → Claude.ai Session…");
        cardLayout.show(this, CARD_UNAVAILABLE);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Must be called on the EDT. */
    public void update(WebUsageResult result) {
        switch (result) {
            case WebUsageResult.Available a -> {
                // Bar expects 0.0–1.0; web API returns 0–100
                fiveHourBar.setValue(a.fiveHourUtil() / 100.0);
                fiveHourBar.setLabel((int) a.fiveHourUtil() + "%");
                fiveHourReset.setText(formatTimeUntil(a.fiveHourReset()));

                sevenDayBar.setValue(a.sevenDayUtil() / 100.0);
                sevenDayBar.setLabel((int) a.sevenDayUtil() + "%");
                sevenDayReset.setText(formatTimeUntil(a.sevenDayReset()));

                cardLayout.show(this, CARD_AVAILABLE);
            }
            case WebUsageResult.Unavailable u -> {
                unavailableLabel.setText(" " + u.reason());
                cardLayout.show(this, CARD_UNAVAILABLE);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Card builders
    // -------------------------------------------------------------------------

    private JPanel buildAvailableCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_SECTION);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        panel.add(sectionTitle("5-HOUR WINDOW"));
        panel.add(fiveHourBar);
        panel.add(styledLabel(fiveHourReset));
        panel.add(sectionTitle("7-DAY WINDOW"));
        panel.add(sevenDayBar);
        panel.add(styledLabel(sevenDayReset));

        fiveHourReset.setText("Resets in: —");
        sevenDayReset.setText("Resets in: —");

        return panel;
    }

    private JPanel buildUnavailableCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_SECTION);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        unavailableLabel.setFont(MONO_SMALL);
        unavailableLabel.setForeground(FG_SECONDARY);
        unavailableLabel.setBackground(BG_SECTION);
        unavailableLabel.setOpaque(true);
        panel.add(unavailableLabel);

        return panel;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String formatTimeUntil(Instant reset) {
        Duration d = Duration.between(Instant.now(), reset);
        if (d.isNegative() || d.isZero()) return "Resetting...";
        long days    = d.toDays();
        long hours   = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) return String.format("Resets in: %dd %dh", days, hours);
        return String.format("Resets in: %dh %dm", hours, minutes);
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(MONO_BOLD);
        label.setForeground(ACCENT);
        return label;
    }

    private static JLabel styledLabel(JLabel label) {
        label.setFont(MONO_SMALL);
        label.setForeground(FG_SECONDARY);
        return label;
    }
}
