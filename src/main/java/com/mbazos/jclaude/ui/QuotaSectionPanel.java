package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.PollResult;
import com.mbazos.jclaude.model.QuotaResult;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.CardLayout;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Adaptive container that shows the correct quota sub-panel based on the
 * {@link QuotaResult} type returned by the latest poll.
 * <p>
 * Uses {@link CardLayout} to swap between sub-panels without re-layout churn.
 */
public class QuotaSectionPanel extends JPanel {

    private static final String CARD_STANDARD          = "standard";
    private static final String CARD_ENTERPRISE_TOKENS = "enterprise_tokens";
    private static final String CARD_ENTERPRISE_BUDGET = "enterprise_budget";
    private static final String CARD_UNAVAILABLE       = "unavailable";
    private static final String CARD_NONE              = "none";

    private final CardLayout            cardLayout            = new CardLayout();
    private final StandardQuotaPanel    standardPanel         = new StandardQuotaPanel();
    private final EnterpriseTokenPanel  enterpriseTokenPanel  = new EnterpriseTokenPanel();
    private final EnterpriseBudgetPanel enterpriseBudgetPanel = new EnterpriseBudgetPanel();
    private final JLabel                unavailableLabel      = new JLabel();
    private final JLabel                noneLabel             = new JLabel(" No API key — Settings → API Key...");

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public QuotaSectionPanel() {
        setLayout(cardLayout);
        setBackground(BG_SECTION);

        styleInfoLabel(unavailableLabel);
        styleInfoLabel(noneLabel);

        add(standardPanel,         CARD_STANDARD);
        add(enterpriseTokenPanel,  CARD_ENTERPRISE_TOKENS);
        add(enterpriseBudgetPanel, CARD_ENTERPRISE_BUDGET);
        add(unavailableLabel,      CARD_UNAVAILABLE);
        add(noneLabel,             CARD_NONE);

        cardLayout.show(this, CARD_NONE);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Must be called on the EDT. Routes the poll result to the correct sub-panel.
     */
    public void update(PollResult result) {
        switch (result) {
            case PollResult.Success(QuotaResult quota) -> {
                switch (quota) {
                    case QuotaResult.Standard s -> {
                        standardPanel.update(s);
                        cardLayout.show(this, CARD_STANDARD);
                    }
                    case QuotaResult.EnterpriseTokens e -> {
                        enterpriseTokenPanel.update(e);
                        cardLayout.show(this, CARD_ENTERPRISE_TOKENS);
                    }
                    case QuotaResult.EnterpriseBudget b -> {
                        enterpriseBudgetPanel.update(b);
                        cardLayout.show(this, CARD_ENTERPRISE_BUDGET);
                    }
                    case QuotaResult.Unavailable u -> {
                        unavailableLabel.setText(" " + u.reason());
                        cardLayout.show(this, CARD_UNAVAILABLE);
                    }
                }
            }
            case PollResult.Failure f -> {
                unavailableLabel.setText(" Error: " + f.message());
                cardLayout.show(this, CARD_UNAVAILABLE);
            }
        }
    }

    /** Resets the panel to the "no API key" state. */
    public void showNone() {
        cardLayout.show(this, CARD_NONE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void styleInfoLabel(JLabel label) {
        label.setFont(MONO_SMALL);
        label.setForeground(FG_SECONDARY);
        label.setBackground(BG_SECTION);
        label.setOpaque(true);
    }
}
