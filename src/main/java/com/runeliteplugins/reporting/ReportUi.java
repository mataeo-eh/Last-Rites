package com.runeliteplugins.reporting;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;

/** Self-contained reporting styles extracted from IRL XP's native form. */
final class ReportUi
{
    static final Color BACKGROUND = new Color(0x17191C);
    static final Color INPUT_BACKGROUND = new Color(0x121417);
    static final Color TEXT = new Color(0xF2F0EA);
    static final Color MUTED_TEXT = new Color(0xA5A9AF);
    static final Color DANGER = new Color(0xD06B68);
    static final Color SELECTION = new Color(0x4B3B20);

    private ReportUi() { }

    static JLabel sectionTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        return label;
    }

    static JLabel mutedLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED_TEXT);
        return label;
    }

    static JTextArea wrappingNote(String text, int rows)
    {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setForeground(MUTED_TEXT);
        area.setFont(new JLabel().getFont());
        area.setRows(rows);
        return area;
    }

    static void styleField(JComponent component)
    {
        component.setBackground(INPUT_BACKGROUND);
        component.setForeground(TEXT);
        component.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x3A3F46)),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
    }

    static void styleSecondaryButton(JButton button)
    {
        button.setBackground(new Color(0x2A2E34));
        button.setForeground(TEXT);
        button.setFocusPainted(false);
        // Custom borders and opacity keep macOS Aqua from painting a white
        // native button underneath the form's light text.
        button.setOpaque(true);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x3A3F46)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
    }

    static void stylePrimaryButton(JButton button)
    {
        styleSecondaryButton(button);
        button.setBackground(new Color(0xD9A441));
        button.setForeground(new Color(0x18130B));
    }
}
