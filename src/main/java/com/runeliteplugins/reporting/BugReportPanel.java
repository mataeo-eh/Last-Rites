package com.runeliteplugins.reporting;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.Component;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** Headless-testable native form. The controller owns persistence and network effects. */
public final class BugReportPanel extends JPanel
{
    private final JTextField title = new JTextField();
    private final JComboBox<BugReport.Category> category = new JComboBox<>(new BugReport.Category[]{BugReport.Category.OTHER});
    private final JTextArea description = textArea(5);
    private final JTextArea steps = textArea(4);
    private final JTextArea expected = textArea(3);
    private final JCheckBox details = new JCheckBox("Include technical details (preview before sending)");
    private final JTextArea preview = textArea(16);
    private final JTextArea status = ReportUi.wrappingNote("", 3);
    private final JButton review = new JButton("Review report");
    private final JButton send = new JButton("Send report");
    private final JButton edit = new JButton("Back to editing");
    private final JButton save = new JButton("Save draft");
    private final JButton discard = new JButton("Discard draft");
    private final JCheckBox consent = new JCheckBox("I agree to send this report");
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final JScrollPane formScroll;
    private final boolean configured;
    private final String titlePrefix;
    private BugReport current = BugReport.empty();
    private JsonObject selectedDiagnostics;
    private boolean dirty;
    private boolean busy;

    public BugReportPanel(boolean configured)
    {
        this(configured, "Plugin");
    }

    public BugReportPanel(boolean configured, String pluginName)
    {
        this.titlePrefix = "[" + pluginName + "] ";
        this.configured = configured;
        setLayout(new BorderLayout(10, 10));
        setBackground(ReportUi.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel form = new Form();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(ReportUi.BACKGROUND);
        form.add(ReportUi.sectionTitle("Tell us what went wrong"));
        form.add(Box.createVerticalStrut(8));
        form.add(ReportUi.wrappingNote("No account or contact details needed. Please leave out passwords, "
            + "account names and other personal information.", 2));
        // Reserve the service's title prefix in the visible character allowance.
        addField(form, "Title (required)", title, 120 - titlePrefix.length());
        addField(form, "Affected area", category, 0);
        category.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list,
                Object value, int index, boolean selected, boolean focused)
            {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setBackground(selected ? ReportUi.SELECTION : ReportUi.INPUT_BACKGROUND);
                setForeground(ReportUi.TEXT);
                return this;
            }
        });
        addField(form, "What happened? (required)", description, 4000);
        addField(form, "Steps to reproduce (optional)", steps, 4000);
        addField(form, "What did you expect? (optional)", expected, 2000);
        details.setOpaque(false);
        details.setForeground(ReportUi.TEXT);
        form.add(details);
        form.add(ReportUi.wrappingNote("Saved drafts stay on this computer. Sending saves a local recovery "
            + "copy until acceptance. Save unfinished text before disabling the plugin or closing RuneLite.", 3));
        for (Component child : form.getComponents())
        {
            if (child instanceof JComponent)
            {
                ((JComponent) child).setAlignmentX(LEFT_ALIGNMENT);
            }
        }
        formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.getViewport().setBackground(ReportUi.BACKGROUND);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        pages.setBackground(ReportUi.BACKGROUND);
        pages.add(formScroll, "edit");

        JPanel reviewPage = new JPanel(new BorderLayout(8, 8));
        reviewPage.setOpaque(false);
        reviewPage.add(ReportUi.wrappingNote("Only the report you review is sent. No logs, screenshots, "
            + "or game/account data are attached automatically. "
            + "A local recovery draft is kept until the report is accepted.", 3), BorderLayout.NORTH);
        preview.setEditable(false);
        ReportUi.styleField(preview);
        preview.setName("reportPreview");
        reviewPage.add(new JScrollPane(preview), BorderLayout.CENTER);
        consent.setName("reportConsent");
        consent.setIcon(new ReportConsentIcon(false));
        consent.setSelectedIcon(new ReportConsentIcon(true));
        consent.setIconTextGap(12);
        consent.setOpaque(true);
        consent.setBackground(new java.awt.Color(0x30281C));
        consent.setForeground(ReportUi.TEXT);
        consent.setFont(consent.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        consent.setBorderPainted(true);
        consent.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new java.awt.Color(0xD9A441), 2),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        consent.getAccessibleContext().setAccessibleDescription("Required: check this box to enable Send report.");
        JPanel confirmation = new JPanel(new BorderLayout(0, 6));
        confirmation.setOpaque(false);
        confirmation.add(consent, BorderLayout.CENTER);
        confirmation.add(ReportUi.wrappingNote("Check the box above to enable Send report.", 1), BorderLayout.SOUTH);
        reviewPage.add(confirmation, BorderLayout.SOUTH);
        pages.add(reviewPage, "review");
        add(pages, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(6, 6));
        footer.setOpaque(false);
        footer.add(status, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(2, 3, 6, 6));
        buttons.setOpaque(false);
        for (JButton button : new JButton[]{save, discard, review, edit, send})
        {
            ReportUi.styleSecondaryButton(button);
            buttons.add(button);
        }
        ReportUi.stylePrimaryButton(review);
        ReportUi.stylePrimaryButton(send);
        footer.add(buttons, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        review.addActionListener(event -> showReview());
        edit.addActionListener(event -> showEditor());
        consent.addActionListener(event -> updateSendState());
        details.addActionListener(event ->
        {
            selectedDiagnostics = details.isSelected() ? BugReport.collectDiagnostics() : null;
            dirty = true;
        });
        category.addActionListener(event -> dirty = true);
        load(BugReport.empty());
    }

    private static JTextArea textArea(int rows)
    {
        JTextArea area = new JTextArea(rows, 35);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new JLabel().getFont());
        return area;
    }

    private void addField(JPanel form, String label, JComponent input, int limit)
    {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        JLabel heading = new JLabel(label);
        heading.setForeground(ReportUi.TEXT);
        heading.setLabelFor(input);
        input.getAccessibleContext().setAccessibleName(label);
        ReportUi.styleField(input);
        row.add(heading, BorderLayout.NORTH);
        row.add(input instanceof JTextArea ? new JScrollPane(input) : input, BorderLayout.CENTER);
        if (input instanceof JTextComponent)
        {
            JTextComponent text = (JTextComponent) input;
            JLabel count = ReportUi.mutedLabel("0 / " + limit);
            row.add(count, BorderLayout.SOUTH);
            text.getDocument().addDocumentListener(new DocumentListener()
            {
                private void changed()
                {
                    dirty = true;
                    count.setText(text.getDocument().getLength() + " / " + limit);
                    count.setForeground(text.getDocument().getLength() > limit ? ReportUi.DANGER : ReportUi.MUTED_TEXT);
                }

                @Override public void insertUpdate(DocumentEvent event) { changed(); }
                @Override public void removeUpdate(DocumentEvent event) { changed(); }
                @Override public void changedUpdate(DocumentEvent event) { changed(); }
            });
        }
        form.add(row);
    }

    public void load(BugReport report)
    {
        current = report;
        title.setText(report.getTitle().startsWith(titlePrefix)
            ? report.getTitle().substring(titlePrefix.length()) : report.getTitle());
        category.setSelectedItem(report.getCategory());
        description.setText(report.getDescription());
        steps.setText(report.getSteps());
        expected.setText(report.getExpectedResult());
        selectedDiagnostics = report.getDiagnostics();
        details.setSelected(selectedDiagnostics != null);
        dirty = false;
        showEditor();
        // JTextArea caret updates can scroll the parent to the last populated
        // field. Restore the beginning after layout so the title is always visible.
        SwingUtilities.invokeLater(() -> formScroll.getViewport().setViewPosition(new Point(0, 0)));
    }

    /** Preserve the UUID for identical retries, including after draft restoration. */
    public BugReport snapshot()
    {
        BugReport candidate = new BugReport(current.getSubmissionId(),
            (BugReport.Category) category.getSelectedItem(), (title.getText().isBlank() ? "" : titlePrefix + title.getText()), description.getText(),
            steps.getText(), expected.getText(), selectedDiagnostics);
        if (!candidate.toJson().equals(current.toJson()))
        {
            candidate = new BugReport(UUID.randomUUID().toString(), candidate.getCategory(),
                candidate.getTitle(), candidate.getDescription(), candidate.getSteps(),
                candidate.getExpectedResult(), candidate.getDiagnostics());
        }
        current = candidate;
        return current;
    }

    private void showReview()
    {
        try
        {
            BugReport report = snapshot();
            report.validateForSubmission();
            // Render the same immutable report submitted by the controller as
            // readable plain text; no JSON or interpreted HTML in the user flow.
            StringBuilder text = new StringBuilder("Title: ").append(report.getTitle())
                .append("\nAffected area: ").append(report.getCategory())
                .append("\n\nWhat happened\n").append(report.getDescription())
                .append("\n\nSteps to reproduce\n").append(report.getSteps())
                .append("\n\nExpected result\n").append(report.getExpectedResult());
            JsonObject technical = report.getDiagnostics();
            if (technical != null)
            {
                text.append("\n\nTechnical details\nReporting revision: ")
                    .append(BugReport.readString(technical, "featureRevision"))
                    .append("\nOperating system: ").append(BugReport.readString(technical, "osName"))
                    .append("\nJava version: ").append(BugReport.readString(technical, "javaVersion"))
                    .append("\nRuneLite version: ").append(BugReport.readString(technical, "runeLiteVersion"));
            }
            text.append("\n\nReport reference: ").append(report.getSubmissionId())
                .append("\nReport format: 1");
            preview.setText(text.toString());
            preview.setCaretPosition(0);
            consent.setSelected(false);
            cards.show(pages, "review");
            review.setVisible(false);
            edit.setVisible(true);
            send.setVisible(true);
            updateSendState();
            status(configured ? "Check the highlighted confirmation box to enable Send report."
                : "Reporting is not available yet. You can save a local draft.");
        }
        catch (IllegalArgumentException ex)
        {
            status(ex.getMessage());
        }
    }

    private void showEditor()
    {
        cards.show(pages, "edit");
        review.setVisible(true);
        edit.setVisible(false);
        send.setVisible(false);
        consent.setSelected(false);
        send.setEnabled(false);
        status(configured ? "Nothing is sent until you review and confirm."
            : "Reporting is not available yet. You can save a local draft.");
    }

    public void setBusy(boolean busy)
    {
        this.busy = busy;
        for (JComponent control : new JComponent[]{title, category, description, steps, expected,
            details, review, edit, save, discard, consent})
        {
            control.setEnabled(!busy);
        }
        updateSendState();
    }

    /** ToolTipManager also handles disabled Swing buttons, so explain the missing step there. */
    private void updateSendState()
    {
        boolean enabled = !busy && configured && consent.isSelected();
        send.setEnabled(enabled);
        send.setBackground(new java.awt.Color(enabled ? 0xD9A441 : 0x33363C));
        send.setForeground(new java.awt.Color(enabled ? 0x18130B : 0xA5A9AF));
        send.setToolTipText(busy ? "Please wait for the current operation to finish."
            : !configured ? "Reporting is not available yet."
            : !consent.isSelected() ? "Check the box labelled \"I agree to send this report\" to enable Send report."
            : "Send the report you reviewed.");
    }

    public void status(String message) { status.setText(message); }
    public boolean isDirty() { return dirty; }
    public void markSaved() { dirty = false; }
    public void onSave(Runnable action) { save.addActionListener(event -> action.run()); }
    public void onDiscard(Runnable action) { discard.addActionListener(event -> action.run()); }
    public void onSend(Runnable action) { send.addActionListener(event -> action.run()); }

    /** Track the viewport width so wrapped notes never force horizontal scrolling. */
    private static final class Form extends JPanel implements Scrollable
    {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
        {
            return Math.max(16, visible.height - 16);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
