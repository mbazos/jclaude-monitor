package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.QuotaResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.time.Duration;
import java.time.Instant;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Displays the 5-hour and 7-day quota bars for the Standard plan.
 */
public class StandardQuotaPanel extends JPanel {

    private final ClaudeProgressBar fiveHourBar   = new ClaudeProgressBar();
    private final JLabel             fiveHourReset = new JLabel();

    private final ClaudeProgressBar sevenDayBar   = new ClaudeProgressBar();
    private final JLabel             sevenDayReset = new JLabel();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public StandardQuotaPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG_SECTION);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(sectionTitle("5-HOUR WINDOW"));
        add(fiveHourBar);
        add(styledLabel(fiveHourReset));
        add(Box.createRigidArea(new Dimension(0, 8)));

        add(sectionTitle("7-DAY WINDOW"));
        add(sevenDayBar);
        add(styledLabel(sevenDayReset));

        // Initialise placeholder text
        fiveHourReset.setText("Resets in: —");
        sevenDayReset.setText("Resets in: —");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Update both bars and reset labels from a fresh {@link QuotaResult.Standard}. */
    public void update(QuotaResult.Standard data) {
        fiveHourBar.setValue(data.fiveHourUtil());
        fiveHourBar.setLabel(String.format("%.1f%%", data.fiveHourUtil() * 100));
        fiveHourReset.setText(formatTimeUntil(data.fiveHourReset()));

        sevenDayBar.setValue(data.sevenDayUtil());
        sevenDayBar.setLabel(String.format("%.1f%%", data.sevenDayUtil() * 100));
        sevenDayReset.setText(formatTimeUntil(data.sevenDayReset()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String formatTimeUntil(Instant reset) {
        Duration d = Duration.between(Instant.now(), reset);
        if (d.isNegative() || d.isZero()) return "Resetting...";
        long days    = d.toDays();
        long hours   = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) return String.format("Resets in: %dd %dh", days, hours);
        return String.format("Resets in: %dh %dm", hours, minutes);
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(MONO_BOLD);
        label.setForeground(ACCENT);
        return label;
    }

    private JLabel styledLabel(JLabel label) {
        label.setFont(MONO_SMALL);
        label.setForeground(FG_SECONDARY);
        return label;
    }
}
