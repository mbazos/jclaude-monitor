package com.mbazos.jclaude.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * A custom terminal-style progress bar that fills with a threshold-based colour
 * and renders a centred label over the bar.
 */
public class ClaudeProgressBar extends JComponent {

    private double value = 0.0;   // 0.0 – 1.0
    private String label = "";    // displayed centred in the bar

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Sets the fill value; clamped to [0.0, 1.0]. */
    public void setValue(double value) {
        this.value = Math.clamp(value, 0.0, 1.0);
        repaint();
    }

    /** Sets the label drawn centred inside the bar. */
    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        repaint();
    }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w      = getWidth();
        int h      = getHeight();
        int filled = (int) (w * value);

        // Background
        g2.setColor(BG_DARK);
        g2.fillRect(0, 0, w, h);

        // Filled portion
        if (filled > 0) {
            g2.setColor(thresholdColor(value));
            g2.fillRect(0, 0, filled, h);
        }

        // Border
        g2.setColor(BORDER);
        g2.drawRect(0, 0, w - 1, h - 1);

        // Centred label — font scales with bar height, capped at 120pt
        float fontSize = Math.min(120f, getHeight() * 0.65f);
        g2.setFont(MONO_PLAIN.deriveFont(fontSize));
        g2.setColor(FG_PRIMARY);
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(label)) / 2;
        int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(label, tx, ty);

        g2.dispose();
    }

    // -------------------------------------------------------------------------
    // Size hints
    // -------------------------------------------------------------------------

    @Override public boolean   isOpaque()          { return true; }
    @Override public Dimension getPreferredSize() { return new Dimension(300, 100); }
    @Override public Dimension getMinimumSize()   { return new Dimension(100,  40); }
    @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Color thresholdColor(double v) {
        if (v < 0.50) return GREEN;
        if (v < 0.75) return YELLOW;
        if (v < 0.90) return ORANGE;
        return RED;
    }
}
