package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.WebUsageResult;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.time.Duration;
import java.time.Instant;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Displays claude.ai usage obtained from the web API.
 * Shows 5-hour and 7-day windows when available (personal accounts), and/or
 * a monthly budget section when extra_usage is enabled (enterprise accounts).
 * Uses {@link CardLayout} to switch between an "available" card and an
 * "unavailable" card (muted reason label).
 */
public class WebUsagePanel extends JPanel {

    private static final String CARD_AVAILABLE   = "available";
    private static final String CARD_UNAVAILABLE = "unavailable";

    private final CardLayout cardLayout = new CardLayout();

    // Section panels (shown/hidden based on account type)
    private final JPanel fiveHourSection   = new JPanel();
    private final JPanel sevenDaySection   = new JPanel();
    private final JPanel extraUsageSection = new JPanel();

    // 5-hour widgets
    private final ClaudeProgressBar fiveHourBar   = new ClaudeProgressBar();
    private final JLabel            fiveHourReset = new JLabel();

    // 7-day widgets
    private final ClaudeProgressBar sevenDayBar   = new ClaudeProgressBar();
    private final JLabel            sevenDayReset = new JLabel();

    // Monthly budget widgets
    private final ClaudeProgressBar extraUsageBar    = new ClaudeProgressBar();
    private final JLabel            extraUsageDetail = new JLabel();

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

        unavailableLabel.setText(" Not logged in — click ⚙ to connect claude.ai");
        cardLayout.show(this, CARD_UNAVAILABLE);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Must be called on the EDT. */
    public void update(WebUsageResult result) {
        switch (result) {
            case WebUsageResult.Available a -> {
                boolean hasFiveHour  = a.fiveHourUtil()  != null;
                boolean hasSevenDay  = a.sevenDayUtil()  != null;
                boolean hasExtraUsage = a.extraUsageEnabled();

                fiveHourSection.setVisible(hasFiveHour);
                if (hasFiveHour) {
                    fiveHourBar.setValue(a.fiveHourUtil() / 100.0);
                    fiveHourBar.setLabel((int) (double) a.fiveHourUtil() + "%");
                    fiveHourReset.setText(formatTimeUntil(a.fiveHourReset()));
                }

                sevenDaySection.setVisible(hasSevenDay);
                if (hasSevenDay) {
                    sevenDayBar.setValue(a.sevenDayUtil() / 100.0);
                    sevenDayBar.setLabel((int) (double) a.sevenDayUtil() + "%");
                    sevenDayReset.setText(formatTimeUntil(a.sevenDayReset()));
                }

                extraUsageSection.setVisible(hasExtraUsage);
                if (hasExtraUsage) {
                    extraUsageBar.setValue(a.extraUsageUtil() / 100.0);
                    extraUsageBar.setLabel(String.format("%.1f%%", a.extraUsageUtil()));
                    double used      = a.usedCreditsCents()   / 100.0;
                    double limit     = a.monthlyLimitCents()  / 100.0;
                    double remaining = limit - used;
                    extraUsageDetail.setText(String.format(
                            "$%.2f / $%.2f  •  $%.2f remaining", used, limit, remaining));
                }

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

        buildSection(fiveHourSection,   "5-HOUR WINDOW",   fiveHourBar,   fiveHourReset,   "Resets in: —");
        buildSection(sevenDaySection,   "7-DAY WINDOW",    sevenDayBar,   sevenDayReset,   "Resets in: —");
        buildSection(extraUsageSection, "MONTHLY BUDGET",  extraUsageBar, extraUsageDetail, "");

        panel.add(fiveHourSection);
        panel.add(sevenDaySection);
        panel.add(extraUsageSection);

        return panel;
    }

    private static void buildSection(JPanel section, String title,
                                     ClaudeProgressBar bar, JLabel detail, String detailDefault) {
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(BG_SECTION);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(MONO_BOLD);
        titleLabel.setForeground(ACCENT);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        // Pin label heights so extra vertical space goes to the bar, not the labels
        titleLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleLabel.getPreferredSize().height));
        section.add(titleLabel);

        bar.setAlignmentX(LEFT_ALIGNMENT);
        section.add(bar);

        detail.setText(detailDefault);
        detail.setFont(MONO_SMALL);
        detail.setForeground(FG_SECONDARY);
        detail.setAlignmentX(LEFT_ALIGNMENT);
        detail.setMaximumSize(new Dimension(Integer.MAX_VALUE, detail.getPreferredSize().height));
        section.add(detail);
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
}
