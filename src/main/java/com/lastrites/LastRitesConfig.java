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

    @ConfigItem(keyName = "duration", name = "Display duration", description = "Seconds after a new death or sample. 0 keeps the pop-up visible until hidden.", position = 0)
    @Range(min = 0, max = 3600)
    default int duration() { return 7; }

    @ConfigItem(keyName = "popupEnabled", name = "Allow death pop-ups", description = "New deaths may display the pop-up. Manually hiding it turns this off; sample deaths still work.", position = 1)
    default boolean popupEnabled() { return true; }

    @ConfigItem(keyName = "lookupTotals", name = "Full totals from HiScores", description = "Look up abbreviated totals using RuneLite's Hardcore Main HiScores client. Enabled by default. HiScores may lag or reflect a reset.", position = 2)
    default boolean lookupTotals() { return true; }

    // Keep chat replacement independent of popup visibility and user exclusions.
    @ConfigItem(keyName = "hideDeathMessages", name = "Hide death messages in chat", description = "Replace recognised Hardcore Worlds death messages in chat with Last Rites pop-ups. Enabled by default. Popup visibility and death exclusions still apply.", position = 3)
    default boolean hideDeathMessages() { return true; }

    @ConfigItem(keyName = "excludeRegex", name = "Hide matching deaths", description = "One Java regex per line, case insensitive. Matches broadcast text and fields such as total=33. Example: \\btotal=33\\b. Invalid rules are shown in the sidebar.", position = 4)
    default String excludeRegex() { return ""; }
}
