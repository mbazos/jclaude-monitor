package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.QuotaResult;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Displays the workspace token quota for the Enterprise Tokens plan.
 */
public class EnterpriseTokenPanel extends JPanel {

    private final ClaudeProgressBar tokenBar   = new ClaudeProgressBar();
    private final JLabel             tokenLabel = new JLabel();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public EnterpriseTokenPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG_SECTION);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("WORKSPACE TOKENS");
        title.setFont(MONO_BOLD);
        title.setForeground(ACCENT);

        tokenLabel.setFont(MONO_SMALL);
        tokenLabel.setForeground(FG_SECONDARY);
        tokenLabel.setText("— of — remaining");

        add(title);
        add(tokenBar);
        add(tokenLabel);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Update bar and label from a fresh {@link QuotaResult.EnterpriseTokens}. */
    public void update(QuotaResult.EnterpriseTokens data) {
        double used = data.limit() > 0
                ? 1.0 - (double) data.remaining() / data.limit()
                : 0.0;

        tokenBar.setValue(used);
        tokenBar.setLabel(String.format("%.1f%%", used * 100));

        tokenLabel.setText(
                formatTokens(data.remaining()) + " of " + formatTokens(data.limit()) + " remaining"
        );
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
