package com.lastrites;

import java.awt.Point;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Recreates RuneLite's real OverlayManager to verify its saved position integration. */
class OverlayPersistenceTest
{
    @Test
    void newOverlayAndManagerRestoreSavedLocation() throws Exception
    {
        Map<String, Object> saved = new HashMap<>();
        ConfigManager config = mock(ConfigManager.class);
        doAnswer(call ->
        {
            saved.put(call.getArgument(0) + "/" + call.getArgument(1), call.getArgument(2));
            return null;
        }).when(config).setConfiguration(anyString(), anyString(), any(Object.class));
        when(config.getConfiguration(anyString(), anyString(), any(Class.class))).thenAnswer(call ->
            saved.get(call.getArgument(0) + "/" + call.getArgument(1)));
        LastRitesConfig settings = mock(LastRitesConfig.class);
        LastRitesOverlay first = new LastRitesOverlay(null, new NoticeQueue(), settings);
        OverlayManager original = manager(config);
        original.add(first);
        first.setPreferredLocation(new Point(190, 240));
        original.saveOverlay(first);
        original.remove(first);
        LastRitesOverlay reopened = new LastRitesOverlay(null, new NoticeQueue(), settings);
        OverlayManager recreated = manager(config);
        recreated.add(reopened);
        assertEquals(new Point(190, 240), reopened.getPreferredLocation());
        assertTrue(reopened.isMovable());
    }

    private static OverlayManager manager(ConfigManager config) throws Exception
    {
        Class<?> snapCorners = Class.forName("net.runelite.client.ui.overlay.SnapCorners");
        Constructor<OverlayManager> constructor = OverlayManager.class.getDeclaredConstructor(
            ConfigManager.class, RuneLiteConfig.class, Client.class, ChatMessageManager.class,
            snapCorners, TooltipOverlay.class, WorldMapOverlay.class);
        constructor.setAccessible(true);
        return constructor.newInstance(config, mock(RuneLiteConfig.class), mock(Client.class),
            mock(ChatMessageManager.class), mock(snapCorners), mock(TooltipOverlay.class),
            mock(WorldMapOverlay.class));
    }
}
