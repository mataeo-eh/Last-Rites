package com.lastrites;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

/** Translates trusted Hardcore broadcasts into local presentation and optional HiScores enrichment. */
@PluginDescriptor(name = "Last Rites", description = "Movable Hardcore Worlds death announcements",
    tags = {"hardcore", "death", "broadcast", "notification"})
public class LastRitesPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private LastRitesConfig config;
    @Inject private ConfigManager configManager;
    @Inject private OverlayManager overlays;
    @Inject private LastRitesOverlay overlay;
    @Inject private NoticeQueue notices;
    @Inject private ClientToolbar toolbar;
    @Inject private HiscoreClient hiscores;
    @Inject private MouseManager mouseManager;
    private final Deque<DeathNotice> lookups = new ArrayDeque<>();
    private NavigationButton navigation;
    private LastRitesPanel panel;
    private long nextId;
    private volatile long lifecycle;
    private volatile boolean running;
    private volatile boolean lookupInProgress;

    @Provides LastRitesConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(LastRitesConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        lifecycle++;
        running = true;
        notices.configure(config.duration(), config.popupEnabled());
        notices.setFilters(config.excludeRegex());
        overlays.add(overlay);
        mouseManager.registerMouseWheelListener(overlay);
        onEdt(() ->
        {
            panel = new LastRitesPanel(notices, config, configManager, this::togglePopup);
            navigation = NavigationButton.builder().tooltip("Last Rites").priority(8)
                .icon(icon()).panel(panel).build();
            toolbar.addNavigation(navigation);
        });
    }

    @Override
    protected void shutDown() throws Exception
    {
        running = false;
        lifecycle++;
        synchronized (lookups) { lookups.clear(); }
        mouseManager.unregisterMouseWheelListener(overlay);
        overlays.remove(overlay);
        notices.clear();
        onEdt(() ->
        {
            if (navigation != null) { toolbar.removeNavigation(navigation); }
            if (panel != null) { panel.stop(); }
            navigation = null;
            panel = null;
        });
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Only server channels on the API's explicit Hardcore world flag qualify.
        // ChatMessage.name/sender/timestamp/messageNode carry no additional death facts.
        if (!running || !client.getWorldType().contains(WorldType.HARDCORE_MAIN)
            || (event.getType() != ChatMessageType.BROADCAST && event.getType() != ChatMessageType.GAMEMESSAGE))
        {
            return;
        }
        BroadcastParser.parse(event.getMessage(), ++nextId).ifPresent(notice ->
        {
            if (notices.add(notice) && notice.hasAbbreviatedTotal() && config.lookupTotals())
            {
                synchronized (lookups)
                {
                    if (lookups.size() >= 25) { lookups.removeFirst(); }
                    lookups.addLast(notice);
                }
            }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (LastRitesConfig.GROUP.equals(event.getGroup()))
        {
            notices.configure(config.duration(), config.popupEnabled());
            notices.setFilters(config.excludeRegex());
            if (!config.lookupTotals()) { synchronized (lookups) { lookups.clear(); } }
        }
    }

    private void togglePopup()
    {
        if (notices.isVisible())
        {
            configManager.setConfiguration(LastRitesConfig.GROUP, "popupEnabled", false);
            notices.hide();
        }
        else
        {
            configManager.setConfiguration(LastRitesConfig.GROUP, "popupEnabled", true);
            notices.showPinned();
        }
    }

    @Schedule(period = 1, unit = ChronoUnit.SECONDS, asynchronous = true)
    public void resolveNextTotal()
    {
        if (!running || !config.lookupTotals() || lookupInProgress) { return; }
        final DeathNotice notice;
        synchronized (lookups) { notice = lookups.pollFirst(); }
        if (notice == null) { return; }
        long generation = lifecycle;
        lookupInProgress = true;
        // One pending result at a time and at most one start per second. RuneLite's
        // API does not expose the HTTP Call; timeout ends waiting, not necessarily I/O.
        // No normal/ironman fallback:
        // a different table can describe a different incarnation of this character.
        hiscores.lookupAsync(notice.player, HiscoreEndpoint.HARDCORE_MAIN)
            .orTimeout(8, TimeUnit.SECONDS).whenComplete((result, error) ->
            {
                try
                {
                    if (running && lifecycle == generation && config.lookupTotals() && error == null && result != null)
                    {
                        // getSkill may return null. Only OVERALL.level is used; player,
                        // rank, XP, all other skills, and activities are intentionally ignored.
                        Skill overall = result.getSkill(HiscoreSkill.OVERALL);
                        if (overall != null && overall.getLevel() > 0)
                        {
                            notices.enrich(notice.id, overall.getLevel());
                        }
                    }
                }
                finally { lookupInProgress = false; }
            });
    }

    private static BufferedImage icon()
    {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xD5B371));
        g.fillOval(3, 2, 18, 17);
        g.fillRect(7, 15, 10, 7);
        g.setColor(new Color(0x211B20));
        g.fillOval(6, 8, 5, 5);
        g.fillOval(14, 8, 5, 5);
        g.dispose();
        return image;
    }

    private static void onEdt(Runnable action) throws Exception
    {
        // Plugin toggles may already run on the EDT; invokeAndWait cannot be nested.
        if (SwingUtilities.isEventDispatchThread()) { action.run(); }
        else { SwingUtilities.invokeAndWait(action); }
    }
}
