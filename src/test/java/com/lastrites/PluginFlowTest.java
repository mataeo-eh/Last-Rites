package com.lastrites;

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
            assertEquals("1.1K", f.queue.current().total);
            result.complete(new HiscoreResult("Big One", Map.of(HiscoreSkill.OVERALL, new Skill(12, 1142, 1000))));
            assertEquals(1142, f.queue.current().hiscoreTotal);
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

    @Test
    void lookupsDefaultOnAndDisablingThemClearsQueuedWorkAndIgnoresInflightResults() throws Exception
    {
        assertTrue(new LastRitesConfig() {}.lookupTotals());
        Fixture f = new Fixture();
        when(f.config.lookupTotals()).thenReturn(false);
        f.plugin.startUp();
        try
        {
            f.plugin.onChatMessage(message(ChatMessageType.BROADCAST,
                "Damis killed Disabled(1K) with a 24 damage hit."));
            f.plugin.resolveNextTotal();
            verifyNoInteractions(f.hiscores);

            when(f.config.lookupTotals()).thenReturn(true);
            CompletableFuture<HiscoreResult> result = new CompletableFuture<>();
            when(f.hiscores.lookupAsync("Enabled", HiscoreEndpoint.HARDCORE_MAIN)).thenReturn(result);
            f.plugin.onChatMessage(message(ChatMessageType.BROADCAST,
                "Damis killed Enabled(1K) with a 24 damage hit."));
            f.plugin.resolveNextTotal();
            verify(f.hiscores).lookupAsync("Enabled", HiscoreEndpoint.HARDCORE_MAIN);
            f.plugin.onChatMessage(message(ChatMessageType.BROADCAST,
                "Damis killed Queued(1K) with a 24 damage hit."));

            when(f.config.lookupTotals()).thenReturn(false);
            net.runelite.client.events.ConfigChanged changed = new net.runelite.client.events.ConfigChanged();
            changed.setGroup(LastRitesConfig.GROUP);
            changed.setKey("lookupTotals");
            f.plugin.onConfigChanged(changed);
            result.complete(new HiscoreResult("Enabled", Map.of(HiscoreSkill.OVERALL, new Skill(12, 1075, 1000))));
            assertTrue(f.queue.history().stream().allMatch(n -> n.hiscoreTotal == null));
            f.plugin.resolveNextTotal();
            // Re-enabling must not resurrect requests queued before disabling.
            when(f.config.lookupTotals()).thenReturn(true);
            f.plugin.resolveNextTotal();
            verifyNoMoreInteractions(f.hiscores);
        }
        finally { f.stop(); }
    }

    @Test
    void replacesDeathChatByDefaultWhilePopupsHistoryAndLookupsStillWork() throws Exception
    {
        Fixture f = new Fixture();
        assertTrue(f.config.hideDeathMessages());
        f.plugin.startUp();
        try
        {
            String death = "Damis killed Big One(1K) with a 24 damage hit.";
            when(f.hiscores.lookupAsync("Big One", HiscoreEndpoint.HARDCORE_MAIN))
                .thenReturn(CompletableFuture.completedFuture(null));
            for (ChatMessageType type : new ChatMessageType[] {ChatMessageType.BROADCAST, ChatMessageType.GAMEMESSAGE})
            {
                f.plugin.onChatMessage(message(type, death));
                int[] stack = f.chatStack(type, death, 1);
                f.filterChat();
                f.filterChat(); // Chat redraws must not ingest the same death again.
                assertArrayEquals(new int[] {87, 0, type.getType(), 42}, stack);
                assertTrue(f.queue.isVisible());
                f.plugin.resolveNextTotal();
            }
            assertEquals(2, f.queue.history().size());
            verify(f.hiscores, times(2)).lookupAsync("Big One", HiscoreEndpoint.HARDCORE_MAIN);
            verify(f.node, never()).setValue(anyString());
            verify(f.client, never()).getObjectStack();
        }
        finally { f.stop(); }
    }

    @Test
    void leavesOtherMessagesWorldsAndCallbacksAloneAndPreservesOtherFilters() throws Exception
    {
        Fixture f = new Fixture();
        f.plugin.startUp();
        try
        {
            String death = "Damis killed X(33) with a 2 damage hit.";
            for (ChatMessageType type : ChatMessageType.values())
            {
                if (type == ChatMessageType.BROADCAST || type == ChatMessageType.GAMEMESSAGE) { continue; }
                int[] stack = f.chatStack(type, death, 1);
                f.filterChat();
                assertEquals(1, stack[1], type.toString());
            }
            int[] unrelated = f.chatStack(ChatMessageType.BROADCAST, "A server announcement.", 1);
            f.filterChat();
            assertEquals(1, unrelated[1]);
            int[] otherWorld = f.chatStack(ChatMessageType.BROADCAST, death, 1);
            when(f.client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
            f.filterChat();
            assertEquals(1, otherWorld[1]);
            when(f.client.getWorldType()).thenReturn(EnumSet.of(WorldType.HARDCORE_MAIN));
            net.runelite.api.events.ScriptCallbackEvent other = new net.runelite.api.events.ScriptCallbackEvent();
            other.setEventName("unrelatedCallback");
            f.plugin.onScriptCallbackEvent(other);
            assertEquals(1, otherWorld[1]);
            int[] blocked = f.chatStack(ChatMessageType.GAMEMESSAGE, "An already hidden message.", 0);
            f.filterChat();
            assertEquals(0, blocked[1]);
            assertTrue(f.queue.history().isEmpty());
            verifyNoInteractions(f.hiscores);
        }
        finally { f.stop(); }
    }

    @Test
    void chatReplacementDoesNotDependOnPopupVisibilityOrExclusions() throws Exception
    {
        Fixture f = new Fixture();
        when(f.config.popupEnabled()).thenReturn(false);
        when(f.config.excludeRegex()).thenReturn("total=33");
        f.plugin.startUp();
        try
        {
            for (String total : new String[] {"33", "34"})
            {
                String death = "Damis killed X(" + total + ") with a 2 damage hit.";
                f.plugin.onChatMessage(message(ChatMessageType.BROADCAST, death));
                int[] stack = f.chatStack(ChatMessageType.BROADCAST, death, 1);
                f.filterChat();
                assertEquals(0, stack[1]);
            }
            assertFalse(f.queue.isVisible());
            assertEquals(1, f.queue.history().size());
            assertEquals("34", f.queue.history().get(0).total);
        }
        finally { f.stop(); }
    }

    @Test
    void refreshesBufferedChatOnToggleAndLifecycleWithoutDestroyingMessages() throws Exception
    {
        Fixture f = new Fixture();
        f.plugin.startUp();
        try
        {
            verify(f.client).refreshChat();
            when(f.config.hideDeathMessages()).thenReturn(false);
            net.runelite.client.events.ConfigChanged changed = new net.runelite.client.events.ConfigChanged();
            changed.setGroup(LastRitesConfig.GROUP);
            changed.setKey("hideDeathMessages");
            f.plugin.onConfigChanged(changed);
            int[] visible = f.chatStack(ChatMessageType.BROADCAST, "Damis killed X(33) with a 2 damage hit.", 1);
            f.filterChat();
            assertEquals(1, visible[1]);
            when(f.config.hideDeathMessages()).thenReturn(true);
            f.plugin.onConfigChanged(changed);
            f.filterChat();
            assertEquals(0, visible[1]);
            verify(f.client, times(3)).refreshChat();
        }
        finally { f.stop(); }
        verify(f.client, times(4)).refreshChat();
        int[] stopped = f.chatStack(ChatMessageType.BROADCAST, "Damis killed X(33) with a 2 damage hit.", 1);
        f.filterChat();
        assertEquals(1, stopped[1]);
        // Starting the same plugin again must restore filtering without cached message ids.
        f.plugin.startUp();
        try
        {
            f.filterChat();
            assertEquals(0, stopped[1]);
            verify(f.client, times(5)).refreshChat();
        }
        finally { f.stop(); }
    }

    @Test
    void ignoresMissingMessageNodesAndIncompleteCallbackStacks() throws Exception
    {
        Fixture f = new Fixture();
        f.plugin.startUp();
        try
        {
            int[] stack = f.chatStack(ChatMessageType.BROADCAST, null, 1);
            f.filterChat();
            assertEquals(1, stack[1]);
            when(f.messages.get(42)).thenReturn(null);
            f.filterChat();
            assertEquals(1, stack[1]);
            when(f.client.getIntStackSize()).thenReturn(2);
            f.filterChat();
            assertEquals(1, stack[1]);
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
        // Exercise real default settings unless a test explicitly overrides one.
        final LastRitesConfig config = mock(LastRitesConfig.class, CALLS_REAL_METHODS);
        final HiscoreClient hiscores = mock(HiscoreClient.class);
        final NoticeQueue queue = new NoticeQueue();
        final net.runelite.api.MessageNode node = mock(net.runelite.api.MessageNode.class);
        // Java erases the table's type argument when Mockito creates this mock.
        @SuppressWarnings("unchecked")
        final net.runelite.api.IterableHashTable<net.runelite.api.MessageNode> messages = mock(net.runelite.api.IterableHashTable.class);

        Fixture() throws Exception
        {
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.HARDCORE_MAIN));
            net.runelite.client.callback.ClientThread clientThread = mock(net.runelite.client.callback.ClientThread.class);
            // Execute scheduled refreshes deterministically without a running game loop.
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
                .when(clientThread).invoke(any(Runnable.class));
            inject(plugin, "client", client);
            inject(plugin, "clientThread", clientThread);
            inject(plugin, "config", config);
            inject(plugin, "configManager", mock(ConfigManager.class));
            inject(plugin, "overlays", mock(OverlayManager.class));
            inject(plugin, "overlay", new LastRitesOverlay(plugin, queue, config));
            inject(plugin, "notices", queue);
            inject(plugin, "toolbar", mock(ClientToolbar.class));
            inject(plugin, "hiscores", hiscores);
            inject(plugin, "mouseManager", mock(net.runelite.client.input.MouseManager.class));
        }

        int[] chatStack(ChatMessageType type, String text, int visible)
        {
            // Include a prefix to verify offsets are relative to stack size.
            int[] stack = {87, visible, type.getType(), 42};
            when(client.getIntStack()).thenReturn(stack);
            when(client.getIntStackSize()).thenReturn(stack.length);
            when(client.getMessages()).thenReturn(messages);
            when(messages.get(42)).thenReturn(node);
            when(node.getValue()).thenReturn(text);
            return stack;
        }

        void filterChat()
        {
            net.runelite.api.events.ScriptCallbackEvent callback = new net.runelite.api.events.ScriptCallbackEvent();
            callback.setEventName("chatFilterCheck");
            plugin.onScriptCallbackEvent(callback);
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
