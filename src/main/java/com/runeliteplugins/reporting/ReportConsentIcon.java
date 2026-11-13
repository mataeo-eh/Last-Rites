package com.runeliteplugins.reporting;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/** High-contrast consent box, independent of small or faint native look-and-feel icons. */
final class ReportConsentIcon implements Icon
{
    private final boolean selected;

    ReportConsentIcon(boolean selected) { this.selected = selected; }
    @Override public int getIconWidth() { return 24; }
    @Override public int getIconHeight() { return 24; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(selected ? new Color(0xD9A441) : new Color(0x121417));
            g.fillRoundRect(x + 1, y + 1, 21, 21, 5, 5);
            g.setColor(new Color(0xF1C971));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x + 1, y + 1, 21, 21, 5, 5);
            if (selected)
            {
                g.setColor(new Color(0x18130B));
                g.setStroke(new BasicStroke(3));
                g.drawLine(x + 5, y + 12, x + 10, y + 17);
                g.drawLine(x + 10, y + 17, x + 19, y + 7);
            }
        }
        finally { g.dispose(); }
    }
}
