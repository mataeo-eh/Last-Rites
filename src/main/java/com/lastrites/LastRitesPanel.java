package com.lastrites;

import com.runeliteplugins.reporting.BugReportController;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

/** Preview, local exclusions, and private bug reporting live beside recent announcements. */
final class LastRitesPanel extends PluginPanel
{
    private final Timer timer;
    private final JTextArea recent = note("", 14);
    private final JTextArea errors = note("", 3);
    private final JTextArea filters = new JTextArea(5, 18);

    LastRitesPanel(NoticeQueue notices, LastRitesConfig config, ConfigManager manager,
        BugReportController reports)
    {
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("LAST RITES");
        title.setForeground(new Color(0xD5B371));
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        controls.add(title);
        controls.add(Box.createVerticalStrut(12));
        controls.add(note("Hardcore deaths, remembered. Alt-drag the pop-up to move it. RuneLite saves its position.", 4));
        JButton preview = button("Show sample death");
        preview.addActionListener(event -> notices.add(NoticeQueue.sample()));
        controls.add(preview);
        JButton position = button("Toggle position preview");
        position.addActionListener(event -> manager.setConfiguration(LastRitesConfig.GROUP,
            "positionPreview", !config.positionPreview()));
        controls.add(position);
        controls.add(Box.createVerticalStrut(12));
        controls.add(note("Hide matching deaths — one regex per line. Example: \\btotal=33\\b\nMatches the broadcast and named fields.", 5));
        filters.setText(config.excludeRegex());
        filters.setLineWrap(true);
        JScrollPane filterScroll = new JScrollPane(filters);
        filterScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        controls.add(filterScroll);
        JButton apply = button("Save filters");
        apply.addActionListener(event -> manager.setConfiguration(LastRitesConfig.GROUP,
            "excludeRegex", filters.getText()));
        controls.add(apply);
        errors.setForeground(new Color(0xD8877C));
        controls.add(errors);
        controls.add(note("Regex uses RE2 syntax: no lookaround or backreferences. Valid rules still work when another rule has an error.", 5));
        controls.add(note("Full totals use Jagex Hardcore Main HiScores. Names are sent to Jagex when this setting is on. A HiScores total may lag or reflect a reset.", 6));
        controls.add(new JLabel("Recent deaths (this session)"));
        JScrollPane recentScroll = new JScrollPane(recent);
        recentScroll.setPreferredSize(new Dimension(200, 260));
        recentScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        controls.add(recentScroll);
        JButton report = button("Report a bug");
        report.addActionListener(event -> reports.open(this));
        controls.add(report);
        // PluginPanel's parent supplies scrolling; all long text wraps to sidebar width.
        add(controls, BorderLayout.NORTH);
        timer = new Timer(700, event ->
        {
            errors.setText(notices.filterErrors());
            StringBuilder text = new StringBuilder();
            for (DeathNotice notice : notices.history())
            {
                text.append(notice.player).append("\n").append(notice.totalLabel()).append("\n");
                if (!notice.killer.isEmpty()) { text.append(notice.killer).append(" · "); }
                text.append(notice.damage).append(" damage\n");
                if (!notice.location.isEmpty()) { text.append(notice.location).append("\n"); }
                text.append("\n");
            }
            String next = text.length() == 0 ? "No deaths received yet." : text.toString();
            if (!next.equals(recent.getText())) { recent.setText(next); }
        });
        timer.start();
    }

    void stop() { timer.stop(); }

    private static JButton button(String text)
    {
        JButton button = new JButton(text);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        button.setAlignmentX(LEFT_ALIGNMENT);
        return button;
    }

    private static JTextArea note(String text, int rows)
    {
        JTextArea area = new JTextArea(text);
        area.setFont(new JLabel().getFont());
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setRows(rows);
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }
}
