package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.QuotaResult;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Displays monthly token counts and estimated spend for the Enterprise Budget plan.
 */
public class EnterpriseBudgetPanel extends JPanel {

    private final JLabel             titleLabel  = new JLabel("SPEND");
    private final JLabel             tokenLabel  = new JLabel();
    private final JLabel             costLabel   = new JLabel();
    private final ClaudeProgressBar  budgetBar   = new ClaudeProgressBar();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public EnterpriseBudgetPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG_SECTION);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        titleLabel.setFont(MONO_BOLD);
        titleLabel.setForeground(ACCENT);

        tokenLabel.setFont(MONO_SMALL);
        tokenLabel.setForeground(FG_SECONDARY);

        costLabel.setFont(MONO_SMALL);
        costLabel.setForeground(FG_SECONDARY);

        add(titleLabel);
        add(tokenLabel);
        add(costLabel);
        add(budgetBar);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Update all labels and bar from a fresh {@link QuotaResult.EnterpriseBudget}. */
    public void update(QuotaResult.EnterpriseBudget data) {
        titleLabel.setText(data.month() + " SPEND");

        tokenLabel.setText(String.format(
                "Input: %s  Output: %s  Cache: %s",
                formatTokens(data.inputTokens()),
                formatTokens(data.outputTokens()),
                formatTokens(data.cacheReadTokens() + data.cacheCreateTokens())
        ));

        if (data.budgetUSD().isPresent() && data.budgetUSD().getAsDouble() > 0) {
            double budget = data.budgetUSD().getAsDouble();
            costLabel.setText(String.format(
                    "Est. cost: ~$%.2f  Budget: $%.2f",
                    data.estimatedCostUSD(), budget
            ));
            double progress = data.estimatedCostUSD() / budget;
            budgetBar.setValue(Math.clamp(progress, 0.0, 1.0));
            budgetBar.setLabel(String.format("$%.2f / $%.2f", data.estimatedCostUSD(), budget));
        } else {
            costLabel.setText(String.format(
                    "Est. cost: ~$%.2f  (no budget set)",
                    data.estimatedCostUSD()
            ));
            budgetBar.setValue(0.0);
            budgetBar.setLabel(String.format("~$%.2f", data.estimatedCostUSD()));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String formatTokens(long n) {
        if (n >= 1_000_000_000L) return String.format("%.1fB", n / 1_000_000_000.0);
        if (n >= 1_000_000L)     return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000L)         return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
