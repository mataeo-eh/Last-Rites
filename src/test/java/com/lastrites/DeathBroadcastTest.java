package com.lastrites;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Observed broadcast fixtures and presentation/filter behavior, independent of live deaths. */
class DeathBroadcastTest
{
    @Test
    void parsesObservedNamedKillerPlayerKillerAndAbbreviatedTotals()
    {
        DeathNotice npc = parse("Damis killed HCW4(1K) with a 24 damage hit in Kandarin.");
        assertEquals("HCW4", npc.player);
        assertEquals("Damis", npc.killer);
        assertEquals("1K", npc.total);
        assertEquals("1K", npc.totalLabel(true));
        assertEquals("Kandarin", npc.location);
        DeathNotice pvp = parse("Pk tester(120) killed Seed Player0(70) with a 25 damage hit in the Wilderness.");
        assertEquals("70", pvp.total);
        assertEquals("Pk tester(120)", pvp.killer);
        DeathNotice named = parse("<col=ef1020>Low Lvl</col>(42) was killed by a 3 damage hit from a Goblin.");
        assertEquals("Low Lvl", named.player);
        assertEquals("a Goblin", named.killer);
        assertEquals(3, named.damage);
    }

    @Test
    void missingCauseStaysEmptyAndUnrecognisedMessagesAreIgnored()
    {
        DeathNotice environmental = parse("Agile Main(33) was killed by a 6 damage hit in Misthalin.");
        assertEquals("", environmental.killer);
        assertEquals("Misthalin", environmental.location);
        assertEquals("", parse("Agile Main(33) was killed by a 6 damage hit.").killer);
        assertFalse(BroadcastParser.parse("Someone completed Cook's Assistant!", 1).isPresent());
        assertFalse(BroadcastParser.parse(null, 1).isPresent());
        assertFalse(BroadcastParser.parse("X(33) was killed by a 9999999999999 damage hit.", 1).isPresent());
    }

    @Test
    void regexFiltersMatchCanonicalTotalWithoutConfusing330AndReportInvalidRules()
    {
        NoticeFilters filters = new NoticeFilters("\\btotal=33\\b\n[\nGoblin");
        assertTrue(filters.excludes(parse("X(33) was killed by a 6 damage hit.")));
        assertFalse(filters.excludes(parse("X(330) was killed by a 6 damage hit.")));
        assertTrue(filters.excludes(parse("A Goblin killed X(330) with a 6 damage hit.")));
        assertTrue(filters.errorText().contains("line 2"));
        NoticeFilters lookahead = new NoticeFilters("\\btotal=(?=33\\b)\\d+\\b");
        assertEquals("", lookahead.errorText());
        assertTrue(lookahead.excludes(parse("X(33) was killed by a 6 damage hit.")));
        assertFalse(lookahead.excludes(parse("X(330) was killed by a 6 damage hit.")));
        NoticeFilters backreference = new NoticeFilters("^([A-Za-z]+) killed \\1\\(");
        assertEquals("", backreference.errorText());
        assertTrue(backreference.excludes(parse("X killed X(33) with a 6 damage hit.")));
    }

    @Test
    void queuesThenEnrichesWithSeparateHiScoresSourceAndReappliesFilters()
    {
        MutableClock clock = new MutableClock();
        NoticeQueue queue = new NoticeQueue(clock);
        queue.add(parse("A Hill Giant killed Big One(1.1K) with a 30 damage hit."));
        queue.enrich(1, 1142);
        assertEquals("1.1K - 1142", queue.current().totalLabel(true));
        assertEquals("1.1K", queue.history().get(0).total);
        clock.now += 7001;
        assertNull(queue.current());
        queue.showSample();
        assertTrue(queue.current().preview);
        queue.clear();
        queue.add(parse("A Hill Giant killed Big One(1.1K) with a 30 damage hit."));
        queue.setFilters("\\btotal=1142\\b");
        queue.enrich(1, 1142);
        assertNull(queue.current());
        queue.clear();
        assertTrue(queue.history().isEmpty());
    }

    @Test
    void rendersPreviewArtifactAndOmitsCardWhenIdle() throws Exception
    {
        NoticeQueue queue = new NoticeQueue();
        LastRitesConfig config = mock(LastRitesConfig.class);
        when(config.duration()).thenReturn(7);
        when(config.lookupTotals()).thenReturn(true);
        LastRitesOverlay overlay = new LastRitesOverlay(null, queue, config);
        BufferedImage canvas = new BufferedImage(420, 220, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try
        {
            assertNull(overlay.render(graphics));
            queue.add(NoticeQueue.sample());
            assertEquals(new Dimension(400, 200), overlay.render(graphics));
            Path output = Path.of("build/previews/death-card.png");
            Files.createDirectories(output.getParent());
            ImageIO.write(canvas, "png", output.toFile());
        }
        finally { graphics.dispose(); }
    }

    private static DeathNotice parse(String message) { return BroadcastParser.parse(message, 1).orElseThrow(); }

    private static final class MutableClock extends Clock
    {
        long now;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }
}
