package com.mbazos.jclaude.ui;

import com.mbazos.jclaude.model.LocalStats;
import com.mbazos.jclaude.model.SessionInfo;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.List;

import static com.mbazos.jclaude.ui.Theme.*;

/**
 * Shows active Claude Code sessions.
 */
public class SessionsPanel extends JPanel {

    private final JLabel titleLabel       = new JLabel("ACTIVE SESSIONS");
    private final JPanel sessionListPanel = new JPanel();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public SessionsPanel() {
        setLayout(new BorderLayout());
        setBackground(BG_SECTION);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        titleLabel.setFont(MONO_BOLD);
        titleLabel.setForeground(ACCENT);

        sessionListPanel.setLayout(new BoxLayout(sessionListPanel, BoxLayout.Y_AXIS));
        sessionListPanel.setBackground(BG_SECTION);

        add(titleLabel,       BorderLayout.NORTH);
        add(sessionListPanel, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Must be called on the EDT. Rebuilds the session list from {@code stats}.
     * {@code stats} may be null if the poll produced a local-data error.
     */
    public void update(LocalStats stats) {
        List<SessionInfo> sessions = (stats != null) ? stats.activeSessions() : List.of();

        // Update title count
        int count = sessions.size();
        titleLabel.setText("ACTIVE SESSIONS" + (count > 0 ? " (" + count + ")" : ""));

        // Rebuild list
        sessionListPanel.removeAll();

        if (sessions.isEmpty()) {
            JLabel empty = new JLabel("  No active sessions");
            empty.setFont(MONO_SMALL);
            empty.setForeground(FG_SECONDARY);
            sessionListPanel.add(empty);
        } else {
            for (SessionInfo session : sessions) {
                sessionListPanel.add(buildSessionRow(session));
            }
        }

        sessionListPanel.revalidate();
        sessionListPanel.repaint();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private JLabel buildSessionRow(SessionInfo session) {
        String status   = session.status().orElse("unknown");
        boolean isBusy  = "busy".equalsIgnoreCase(status);
        String dot      = isBusy ? "●" : "○";   // ● or ○
        String name     = lastPathComponent(session.cwd());

        // Build the label text with fixed-width spacing
        String text = dot + " " + name + "  [" + status + "]";

        JLabel label = new JLabel(text);
        label.setFont(MONO_PLAIN);
        label.setForeground(isBusy ? ACCENT : FG_SECONDARY);
        return label;
    }

    private static String lastPathComponent(String cwd) {
        if (cwd == null || cwd.isBlank()) return "(unknown)";
        try {
            Path p = Path.of(cwd);
            Path filename = p.getFileName();
            return filename != null ? filename.toString() : cwd;
        } catch (Exception e) {
            // Fall back to simple string split
            int slash = cwd.lastIndexOf('/');
            return slash >= 0 && slash < cwd.length() - 1 ? cwd.substring(slash + 1) : cwd;
        }
    }
}
