package com.lastrites;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Resizable achievement-style history viewport; RuneLite owns dragging and saved size/position. */
public final class LastRitesOverlay extends Overlay implements MouseWheelListener
{
    private static final Color GOLD = new Color(0xD5B371);
    private static final int SCROLL_UNIT = 16;
    private final NoticeQueue notices;
    private final LastRitesConfig config;
    private double scrollOffset;
    private int maximumScroll;
    private long lastRevision = -1;

    @Inject
    LastRitesOverlay(LastRitesPlugin plugin, NoticeQueue notices, LastRitesConfig config)
    {
        super(plugin);
        this.notices = notices;
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
        // The same OverlayManager resize/position machinery used by IRL XP.
        // A custom-painted Overlay must apply preferredSize itself (OverlayPanel does so in super.render).
        setResizable(true);
        setMinimumSize(180);
    }

    @Override
    public synchronized Dimension render(Graphics2D graphics)
    {
        List<DeathNotice> rows = notices.visibleNotices();
        if (rows.isEmpty()) { return null; }
        if (lastRevision != notices.revision())
        {
            scrollOffset = 0;
            lastRevision = notices.revision();
        }
        Dimension preferred = getPreferredSize();
        int width = preferred == null ? 400 : Math.max(180, preferred.width);
        int height = preferred == null ? 200 : Math.max(180, preferred.height);
        float scale = Math.max(.7f, Math.min(1.8f, width / 400f));
        int header = Math.round(43 * scale);
        int footer = Math.round(23 * scale);
        int rowHeight = Math.round(105 * scale);
        int viewportHeight = height - header - footer;
        maximumScroll = Math.max(0, rows.size() * rowHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(maximumScroll, scrollOffset));
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(0x38262A), 0, height, new Color(0x14171D)));
            g.fillRoundRect(1, 1, width - 2, height - 2, 12, 12);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(4, 4, width - 9, height - 9, 9, 9);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(11 * scale)));
            g.drawString(fit(g, "LAST RITES / HARDCORE FALLEN", width - 32), 16, Math.round(26 * scale));
            g.setColor(new Color(0x775B48));
            g.drawLine(12, header - 1, width - 13, header - 1);
            Graphics2D body = (Graphics2D) g.create();
            try
            {
                body.clipRect(10, header, width - 23, viewportHeight);
                for (int i = 0; i < rows.size(); i++)
                {
                    int y = header + i * rowHeight - (int) scrollOffset;
                    if (y + rowHeight < header || y > height - footer) { continue; }
                    drawDeath(body, rows.get(i), y, width, rowHeight, scale);
                }
            }
            finally { body.dispose(); }
            g.setColor(new Color(0xA49A91));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(10 * scale)));
            String hint = rows.size() > 1 ? "Scroll to browse recent deaths" : rows.get(0).preview ? "Sample death" : "Latest death";
            g.drawString(fit(g, hint, width - 32), 16, height - Math.round(9 * scale));
            if (maximumScroll > 0)
            {
                int thumbHeight = Math.max(12, viewportHeight * viewportHeight / (rows.size() * rowHeight));
                int thumbY = header + (int) ((viewportHeight - thumbHeight) * scrollOffset / maximumScroll);
                g.setColor(GOLD);
                g.fillRoundRect(width - 12, thumbY, 3, thumbHeight, 3, 3);
            }
        }
        finally { g.dispose(); }
        return new Dimension(width, height);
    }

    private void drawDeath(Graphics2D g, DeathNotice notice, int y, int width, int rowHeight, float scale)
    {
        g.setFont(new Font(Font.SERIF, Font.BOLD, Math.round(21 * scale)));
        g.setColor(new Color(0xFFF1D4));
        g.drawString(fit(g, notice.playerLabel(config.lookupTotals()), width - 37), 16, y + Math.round(28 * scale));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(13 * scale)));
        g.setColor(new Color(0xE7DDCF));
        String cause = notice.killer.isEmpty() ? "" : "Slain by " + notice.killer + "  ·  ";
        g.drawString(fit(g, cause + notice.damage + " damage", width - 37), 16, y + Math.round(53 * scale));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(12 * scale)));
        g.setColor(new Color(0xA49A91));
        String location = notice.preview ? "PREVIEW" + (notice.location.isEmpty() ? "" : " · " + notice.location) : notice.location;
        if (!location.isEmpty()) { g.drawString(fit(g, location, width - 37), 16, y + Math.round(76 * scale)); }
        g.setColor(new Color(0x59443A));
        g.drawLine(16, y + rowHeight - 5, width - 20, y + rowHeight - 5);
    }

    @Override
    public synchronized MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
    {
        // MouseManager supplies canvas coordinates after stretched-mode translation.
        // Consume only over this visible overlay so world-camera scrolling still works elsewhere.
        Rectangle bounds = new Rectangle(getBounds());
        if (!event.isConsumed() && notices.isVisible() && bounds.contains(event.getPoint()))
        {
            double units = event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
                ? Math.max(1, bounds.height - 60) : SCROLL_UNIT * Math.max(1, event.getScrollAmount());
            scrollOffset = Math.max(0, Math.min(maximumScroll,
                scrollOffset + event.getPreciseWheelRotation() * units));
            event.consume();
        }
        return event;
    }

    private static String fit(Graphics2D g, String text, int width)
    {
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= width) { return text; }
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + "…") > width) { end--; }
        return text.substring(0, end) + "…";
    }
}
