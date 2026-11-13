package com.lastrites;

import com.runeliteplugins.reporting.BugReportController;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** End-to-end event routing and external lookup behavior with isolated clients/services. */
class PluginFlowTest
{
    @Test
    void startsAndStopsOnEdtAndGatesServerMessagesToHardcoreWorlds() throws Exception
    {
        Fixture f = new Fixture();
        SwingUtilities.invokeAndWait(() ->
        {
            try { f.plugin.startUp(); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        try
        {
            when(f.client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
            f.plugin.onChatMessage(message(ChatMessageType.BROADCAST, "Damis killed X(33) with a 2 damage hit."));
            when(f.client.getWorldType()).thenReturn(EnumSet.of(WorldType.HARDCORE_MAIN));
            f.plugin.onChatMessage(message(ChatMessageType.PUBLICCHAT, "Damis killed X(33) with a 2 damage hit."));
            assertTrue(f.queue.history().isEmpty());
            f.queue.setFilters("\\btotal=33\\b");
            f.plugin.onChatMessage(message(ChatMessageType.BROADCAST, "Damis killed X(33) with a 2 damage hit."));
            assertTrue(f.queue.history().isEmpty());
            f.plugin.onChatMessage(message(ChatMessageType.GAMEMESSAGE, "Damis killed X(34) with a 2 damage hit."));
            assertEquals(1, f.queue.history().size());
            assertEquals("34", f.queue.history().get(0).total);
            verifyNoInteractions(f.hiscores);
        }
        finally { f.stop(); }
        assertTrue(f.queue.history().isEmpty());
        verify(f.reports).shutDown();
    }

    @Test
    void looksUpOnlyAbbreviationsOnCorrectTableAndPreservesSource() throws Exception
    {
        Fixture f = new Fixture();
        f.plugin.startUp();
        try
        {
            CompletableFuture<HiscoreResult> result = new CompletableFuture<>();
            when(f.hiscores.lookupAsync("Big One", HiscoreEndpoint.HARDCORE_MAIN)).thenReturn(result);
            f.plugin.onChatMessage(message(ChatMessageType.BROADCAST, "Damis killed Big One(1.1K) with a 24 damage hit."));
            f.plugin.resolveNextTotal();
            assertEquals("1.1K", f.queue.current(7, false).total);
            result.complete(new HiscoreResult("Big One", Map.of(HiscoreSkill.OVERALL, new Skill(12, 1142, 1000))));
            assertEquals(1142, f.queue.current(7, false).hiscoreTotal);
            verify(f.hiscores).lookupAsync("Big One", HiscoreEndpoint.HARDCORE_MAIN);
        }
        finally { f.stop(); }
    }

    @Test
    void unavailableMissingAndStaleLookupsKeepBroadcastOrRespectShutdown() throws Exception
    {
        Fixture f = new Fixture();
        f.plugin.startUp();
        try
        {
            when(f.hiscores.lookupAsync(anyString(), eq(HiscoreEndpoint.HARDCORE_MAIN)))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.completedFuture(new HiscoreResult("X", Map.of())))
                .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("unavailable")));
            for (int i = 0; i < 3; i++)
            {
                f.plugin.onChatMessage(message(ChatMessageType.BROADCAST, "Damis killed X(1K) with a 24 damage hit."));
                f.plugin.resolveNextTotal();
            }
            assertTrue(f.queue.history().stream().allMatch(n -> n.hiscoreTotal == null));
            CompletableFuture<HiscoreResult> late = new CompletableFuture<>();
            when(f.hiscores.lookupAsync(anyString(), eq(HiscoreEndpoint.HARDCORE_MAIN))).thenReturn(late);
            f.plugin.onChatMessage(message(ChatMessageType.BROADCAST, "Damis killed X(1K) with a 24 damage hit."));
            f.plugin.resolveNextTotal();
            f.stop();
            late.complete(new HiscoreResult("X", Map.of(HiscoreSkill.OVERALL, new Skill(12, 1142, 1000))));
            assertTrue(f.queue.history().isEmpty());
        }
        finally { f.stop(); }
    }

    private static ChatMessage message(ChatMessageType type, String text)
    {
        return new ChatMessage(null, type, "", text, "", 0);
    }

    private static void inject(Object owner, String name, Object value) throws Exception
    {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static final class Fixture
    {
        final LastRitesPlugin plugin = new LastRitesPlugin();
        final Client client = mock(Client.class);
        final LastRitesConfig config = mock(LastRitesConfig.class);
        final HiscoreClient hiscores = mock(HiscoreClient.class);
        final BugReportController reports = mock(BugReportController.class);
        final NoticeQueue queue = new NoticeQueue();

        Fixture() throws Exception
        {
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.HARDCORE_MAIN));
            when(config.excludeRegex()).thenReturn("");
            when(config.lookupTotals()).thenReturn(true);
            inject(plugin, "client", client);
            inject(plugin, "config", config);
            inject(plugin, "configManager", mock(ConfigManager.class));
            inject(plugin, "overlays", mock(OverlayManager.class));
            inject(plugin, "overlay", new LastRitesOverlay(plugin, queue, config));
            inject(plugin, "notices", queue);
            inject(plugin, "toolbar", mock(ClientToolbar.class));
            inject(plugin, "reports", reports);
            inject(plugin, "hiscores", hiscores);
        }

        void stop() throws Exception
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try { plugin.shutDown(); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
        }
    }
}
