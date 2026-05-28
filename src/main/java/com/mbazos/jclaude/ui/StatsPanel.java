package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.DailyStats;
import com.mbazos.jclaude.model.LocalStats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.time.LocalDate;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Displays today's stats and all-time stats in two labelled sections.
 */
public class StatsPanel extends JPanel {

    // Today section
    private final JLabel todayTitle    = new JLabel("TODAY");
    private final JLabel todayMessages = new JLabel("Messages: —");
    private final JLabel todaySessions = new JLabel("Sessions: —");
    private final JLabel todayTools    = new JLabel("Tool calls: —");
    private final JLabel todayTokens   = new JLabel("Tokens: —");

    // All-time section
    private final JLabel totalTitle    = new JLabel("ALL-TIME");
    private final JLabel totalSessions = new JLabel("Sessions: —");
    private final JLabel totalMessages = new JLabel("Messages: —");

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public StatsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG_SECTION);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        styleTitle(todayTitle);
        styleTitle(totalTitle);

        styleData(todayMessages);
        styleData(todaySessions);
        styleData(todayTools);
        styleData(todayTokens);
        styleData(totalSessions);
        styleData(totalMessages);

        // Update today title with today's date
        todayTitle.setText("TODAY (" + LocalDate.now() + ")");

        add(todayTitle);
        add(todayMessages);
        add(todaySessions);
        add(todayTools);
        add(todayTokens);

        add(Box.createRigidArea(new Dimension(0, 8)));

        add(totalTitle);
        add(totalSessions);
        add(totalMessages);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Must be called on the EDT. {@code stats} may be null on a local-read error.
     */
    public void update(LocalStats stats) {
        // Update today's date in title (may have changed day)
        todayTitle.setText("TODAY (" + LocalDate.now() + ")");

        if (stats == null) {
            clearLabels();
            return;
        }

        // Today stats
        DailyStats today = stats.today();
        if (today != null) {
            todayMessages.setText("Messages: " + String.format("%,d", today.messageCount()));
            todaySessions.setText("Sessions: " + String.format("%,d", today.sessionCount()));
            todayTools.setText("Tool calls: " + String.format("%,d", today.toolCallCount()));
            todayTokens.setText("Tokens: " + formatTokens(today.totalTokens()));
        } else {
            todayMessages.setText("Messages: —");
            todaySessions.setText("Sessions: —");
            todayTools.setText("Tool calls: —");
            todayTokens.setText("Tokens: —");
        }

        // All-time stats
        totalSessions.setText("Sessions: " + String.format("%,d", stats.totalSessions()));
        totalMessages.setText("Messages: " + String.format("%,d", stats.totalMessages()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void clearLabels() {
        todayMessages.setText("Messages: —");
        todaySessions.setText("Sessions: —");
        todayTools.setText("Tool calls: —");
        todayTokens.setText("Tokens: —");
        totalSessions.setText("Sessions: —");
        totalMessages.setText("Messages: —");
    }

    private static void styleTitle(JLabel label) {
        label.setFont(MONO_BOLD);
        label.setForeground(ACCENT);
    }

    private static void styleData(JLabel label) {
        label.setFont(MONO_PLAIN);
        label.setForeground(FG_PRIMARY);
    }

    private static String formatTokens(long n) {
        if (n >= 1_000_000_000L) return String.format("%.1fB", n / 1_000_000_000.0);
        if (n >= 1_000_000L)     return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000L)         return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
