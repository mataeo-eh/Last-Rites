package com.lastrites;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/** User settings are persisted by RuneLite; overlay position uses OverlayManager. */
@ConfigGroup(LastRitesConfig.GROUP)
public interface LastRitesConfig extends Config
{
    String GROUP = "lastrites";

    @ConfigItem(keyName = "duration", name = "Display duration", description = "Seconds per death announcement", position = 0)
    @Range(min = 2, max = 30)
    default int duration() { return 7; }

    @ConfigItem(keyName = "positionPreview", name = "Position preview", description = "Keep a sample visible so you can Alt-drag it into place", position = 1)
    default boolean positionPreview() { return false; }

    @ConfigItem(keyName = "lookupTotals", name = "Look up full totals", description = "Send broadcast player names to Jagex Hardcore Main HiScores for full totals. HiScores may lag or reflect a reset.", position = 2)
    default boolean lookupTotals() { return true; }

    @ConfigItem(keyName = "excludeRegex", name = "Hide matching deaths", description = "One RE2 regex per line, case insensitive. Matches broadcast text and fields such as total=33. Example: \\btotal=33\\b. Invalid rules are shown in the sidebar.", position = 3)
    default String excludeRegex() { return ""; }
}
