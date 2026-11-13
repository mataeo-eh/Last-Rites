package com.lastrites;

import com.runeliteplugins.reporting.BugReportController;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
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
        BugReportController reports, Runnable togglePopup)
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
        controls.add(note("Move: Windows Alt + drag; Mac Option + drag. Hold the same key and drag an edge to resize. RuneLite saves the size and position.", 6));
        JLabel visibility = new JLabel();
        visibility.setName("popupVisibility");
        controls.add(visibility);
        JCheckBox automatic = new JCheckBox("Show on new deaths", config.popupEnabled());
        automatic.setName("popupAutomatic");
        automatic.setOpaque(false);
        automatic.setToolTipText("Uncheck to keep the pop-up hidden on new deaths. Sample deaths still work.");
        automatic.addActionListener(event -> {
            manager.setConfiguration(LastRitesConfig.GROUP, "popupEnabled", automatic.isSelected());
            notices.configure(config.duration(), automatic.isSelected());
        });
        controls.add(automatic);
        JButton position = button("Show pop-up");
        position.setName("popupToggle");
        position.addActionListener(event -> togglePopup.run());
        controls.add(position);
        JButton preview = button("Show sample death");
        preview.setName("popupSample");
        preview.addActionListener(event -> notices.showSample());
        controls.add(preview);
        controls.add(Box.createVerticalStrut(8));
        controls.add(new JLabel("Display duration (seconds)"));
        JSpinner duration = new JSpinner(new SpinnerNumberModel(Math.max(0, Math.min(3600, config.duration())), 0, 3600, 1));
        duration.setName("popupDuration");
        duration.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        duration.addChangeListener(event -> manager.setConfiguration(LastRitesConfig.GROUP,
            "duration", ((Number) duration.getValue()).intValue()));
        controls.add(duration);
        controls.add(note("0 seconds keeps the pop-up visible. Default: 7 seconds. Manually showing it keeps it open until hidden.", 5));
        JCheckBox fullTotals = new JCheckBox("Show full totals", config.lookupTotals());
        fullTotals.setName("popupFullTotals");
        fullTotals.setOpaque(false);
        fullTotals.addActionListener(event -> manager.setConfiguration(LastRitesConfig.GROUP,
            "lookupTotals", fullTotals.isSelected()));
        controls.add(fullTotals);
        controls.add(Box.createVerticalStrut(12));
        controls.add(note("Hide matching deaths — one regex per line. Example: \\btotal=33\\b\nMatches the broadcast and named fields.", 5));
        filters.setText(config.excludeRegex());
        filters.setLineWrap(true);
        JScrollPane filterScroll = new JScrollPane(filters);
        filterScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        filterScroll.getVerticalScrollBar().setUnitIncrement(16);
        handOffWheelAtEdges(filterScroll);
        controls.add(filterScroll);
        JButton apply = button("Save filters");
        apply.addActionListener(event -> manager.setConfiguration(LastRitesConfig.GROUP,
            "excludeRegex", filters.getText()));
        controls.add(apply);
        errors.setForeground(new Color(0xD8877C));
        controls.add(errors);
        controls.add(note("Regex uses RE2 syntax: no lookaround or backreferences. Valid rules still work when another rule has an error.", 5));
        controls.add(note("Full totals appear after the broadcast total, for example (1K) - 1075. HiScores may lag or reflect a reset.", 5));
        controls.add(new JLabel("Recent deaths (this session)"));
        JScrollPane recentScroll = new JScrollPane(recent);
        recentScroll.setPreferredSize(new Dimension(200, 260));
        recentScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        recentScroll.getVerticalScrollBar().setUnitIncrement(16);
        handOffWheelAtEdges(recentScroll);
        controls.add(recentScroll);
        JButton report = button("Report a bug");
        report.addActionListener(event -> reports.open(this));
        controls.add(report);
        // PluginPanel's parent supplies scrolling; all long text wraps to sidebar width.
        add(controls, BorderLayout.NORTH);
        getScrollPane().getVerticalScrollBar().setUnitIncrement(16);
        timer = new Timer(150, event ->
        {
            boolean shown = notices.isVisible();
            visibility.setText(shown ? "Pop-up: visible" : "Pop-up: hidden");
            visibility.setForeground(shown ? new Color(0x82C58D) : new Color(0xC9AD7A));
            position.setText(shown ? "Hide pop-up" : "Show pop-up");
            automatic.setSelected(config.popupEnabled());
            fullTotals.setSelected(config.lookupTotals());
            if (!duration.hasFocus() && !((JSpinner.DefaultEditor) duration.getEditor()).getTextField().hasFocus()
                && ((Number) duration.getValue()).intValue() != config.duration())
            {
                duration.setValue(config.duration());
            }
            errors.setText(notices.filterErrors());
            StringBuilder text = new StringBuilder();
            for (DeathNotice notice : notices.history())
            {
                text.append(notice.playerLabel(config.lookupTotals())).append("\n");
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

    /** Match IRL XP: nested panes pass wheel input to the sidebar at their edges. */
    private static void handOffWheelAtEdges(JScrollPane pane)
    {
        pane.addMouseWheelListener(event -> {
            double rotation = event.getPreciseWheelRotation();
            javax.swing.JScrollBar bar = pane.getVerticalScrollBar();
            boolean canScroll = bar.isVisible() && (rotation < 0 ? bar.getValue() > bar.getMinimum()
                : bar.getValue() + bar.getVisibleAmount() < bar.getMaximum());
            if (rotation != 0 && !canScroll && pane.getParent() != null)
            {
                pane.getParent().dispatchEvent(javax.swing.SwingUtilities.convertMouseEvent(pane, event, pane.getParent()));
            }
        });
    }

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
