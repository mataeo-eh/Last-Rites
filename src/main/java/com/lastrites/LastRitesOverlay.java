package com.lastrites;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Achievement-style death card. RuneLite owns Alt-dragging and position persistence. */
public final class LastRitesOverlay extends Overlay
{
    private static final int WIDTH = 400;
    private static final Color GOLD = new Color(0xD5B371);
    private final NoticeQueue notices;
    private final LastRitesConfig config;

    @Inject
    LastRitesOverlay(LastRitesPlugin plugin, NoticeQueue notices, LastRitesConfig config)
    {
        super(plugin);
        this.notices = notices;
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        DeathNotice notice = notices.current(Math.max(2, Math.min(30, config.duration())), config.positionPreview());
        if (notice == null) { return null; }
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(0x38262A), 0, 150, new Color(0x14171D)));
            g.fillRoundRect(1, 1, WIDTH - 2, 150, 12, 12);
            g.setColor(new Color(0x6E4539));
            g.setStroke(new BasicStroke(3));
            g.drawRoundRect(2, 2, WIDTH - 5, 147, 12, 12);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(5, 5, WIDTH - 11, 141, 9, 9);
            // Original vector skull keeps the card legible without borrowed game artwork.
            g.setColor(GOLD);
            g.fillOval(18, 20, 32, 29);
            g.fillRoundRect(25, 39, 18, 17, 3, 3);
            g.setColor(new Color(0x201C22));
            g.fillOval(23, 31, 8, 9);
            g.fillOval(37, 31, 8, 9);
            g.drawLine(31, 47, 31, 54);
            g.drawLine(37, 47, 37, 54);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.setColor(GOLD);
            g.drawString(notice.preview ? "LAST RITES  /  PREVIEW" : "LAST RITES  /  HARDCORE FALLEN", 64, 27);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 23));
            g.setColor(new Color(0xFFF1D4));
            g.drawString(fit(g, notice.player, WIDTH - 82), 64, 54);
            g.setColor(new Color(0x775B48));
            g.drawLine(18, 65, WIDTH - 19, 65);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(new Color(0xE7DDCF));
            String cause = notice.killer.isEmpty() ? "" : "Slain by " + notice.killer + "  ·  ";
            g.drawString(fit(g, cause + notice.damage + " damage", WIDTH - 36), 18, 88);
            g.setColor(new Color(0xC6B598));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString(fit(g, notice.totalLabel(), WIDTH - 36), 18, 109);
            if (!notice.location.isEmpty())
            {
                g.setColor(new Color(0xA49A91));
                g.drawString(fit(g, notice.location, WIDTH - 36), 18, 130);
            }
        }
        finally { g.dispose(); }
        return new Dimension(WIDTH, 152);
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
