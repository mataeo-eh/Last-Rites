package com.lastrites;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the user's manual-hide, sample override, timer, and history browsing contracts. */
class PopupLifecycleTest
{
    @Test
    void timerExpiryAllowsNextDeathButManualHidingBlocksIt()
    {
        MutableClock clock = new MutableClock();
        NoticeQueue state = new NoticeQueue(clock);
        state.configure(7, true);
        state.add(death(1));
        assertTrue(state.isVisible());
        clock.now = 7000;
        assertFalse(state.isVisible());
        state.add(death(2));
        assertTrue(state.isVisible());
        state.hide();
        state.add(death(3));
        assertFalse(state.isVisible());
        assertEquals(3, state.history().size());
    }

    @Test
    void sampleOverridesHidingWithoutEnablingAutomaticDeathsAndZeroNeverExpires()
    {
        MutableClock clock = new MutableClock();
        NoticeQueue state = new NoticeQueue(clock);
        state.configure(2, false);
        state.showSample();
        assertTrue(state.isVisible());
        clock.now = 1000;
        state.add(death(1));
        clock.now = 2000;
        assertFalse(state.isVisible());
        state.add(death(2));
        assertFalse(state.isVisible());
        state.configure(0, false);
        state.showSample();
        clock.now = 86400000;
        assertTrue(state.isVisible());
        state.hide();
        assertFalse(state.isVisible());
        state.configure(2, false);
        state.showPinned();
        clock.now += 86400000;
        state.add(death(3));
        clock.now += 86400000;
        assertTrue(state.isVisible());
    }

    @Test
    void eachNewDeathRestartsTimerAndChangingDurationAppliesToCurrentDisplay()
    {
        MutableClock clock = new MutableClock();
        NoticeQueue state = new NoticeQueue(clock);
        state.configure(7, true);
        state.add(death(1));
        clock.now = 6000;
        state.add(death(2));
        clock.now = 12000;
        assertTrue(state.isVisible());
        state.configure(0, true);
        clock.now = 999999;
        assertTrue(state.isVisible());
        state.configure(2, true);
        assertFalse(state.isVisible());
    }

    @Test
    void broadcastAndFullTotalsStayCompactAndToggleIndependently()
    {
        DeathNotice notice = new DeathNotice(1, "Example", "1K", "", 4, "", "", 1075, false);
        assertEquals("Example (1K) - 1075", notice.playerLabel(true));
        assertEquals("Example (1K)", notice.playerLabel(false));
        assertEquals("1K", notice.totalLabel(false));
    }

    @Test
    void wheelScrollsOnlyVisiblePopupAndResizedViewportHonoursSavedSize()
    {
        NoticeQueue state = new NoticeQueue();
        state.configure(0, true);
        for (int i = 0; i < 60; i++) { state.add(death(i)); }
        assertEquals(50, state.history().size());
        LastRitesConfig settings = mock(LastRitesConfig.class);
        LastRitesOverlay overlay = new LastRitesOverlay(null, state, settings);
        overlay.setPreferredSize(new Dimension(280, 360));
        BufferedImage before = render(overlay);
        overlay.getBounds().setBounds(10, 10, 280, 360);
        MouseWheelEvent outside = wheel(500, 500, 1);
        assertSame(outside, overlay.mouseWheelMoved(outside));
        assertFalse(outside.isConsumed());
        MouseWheelEvent inside = wheel(50, 100, .5);
        overlay.mouseWheelMoved(inside);
        assertTrue(inside.isConsumed());
        BufferedImage after = render(overlay);
        assertFalse(Arrays.equals(((DataBufferInt) before.getRaster().getDataBuffer()).getData(),
            ((DataBufferInt) after.getRaster().getDataBuffer()).getData()));
        assertEquals(new Dimension(280, 360), overlay.getPreferredSize());
        assertTrue(overlay.isResizable());
        state.hide();
        MouseWheelEvent hidden = wheel(50, 100, 1);
        overlay.mouseWheelMoved(hidden);
        assertFalse(hidden.isConsumed());
    }

    private static DeathNotice death(long id)
    {
        return new DeathNotice(id, "Player " + id, "33", "Goblin", 3, "Misthalin", "", null, false);
    }

    private static MouseWheelEvent wheel(int x, int y, double rotation)
    {
        return new MouseWheelEvent(new Canvas(), MouseEvent.MOUSE_WHEEL, 0, 0,
            x, y, x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, (int) rotation, rotation);
    }

    private static BufferedImage render(LastRitesOverlay overlay)
    {
        BufferedImage image = new BufferedImage(300, 380, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { assertEquals(new Dimension(280, 360), overlay.render(g)); }
        finally { g.dispose(); }
        return image;
    }

    private static final class MutableClock extends Clock
    {
        long now;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }
}
