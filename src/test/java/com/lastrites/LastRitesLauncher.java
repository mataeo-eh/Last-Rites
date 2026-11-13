package com.lastrites;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Registers the local plugin in the development client; credentials stay in RuneLite. */
public final class LastRitesLauncher
{
    private LastRitesLauncher() { }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(LastRitesPlugin.class);
        RuneLite.main(args);
    }
}
