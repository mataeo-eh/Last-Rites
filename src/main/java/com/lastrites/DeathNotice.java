package com.lastrites;

/** Immutable facts from one broadcast; a HiScores total is a separately sourced value. */
public final class DeathNotice
{
    final long id;
    final String player;
    final String total;
    final String killer;
    final int damage;
    final String location;
    final String original;
    final Integer hiscoreTotal;
    final boolean preview;

    DeathNotice(long id, String player, String total, String killer, int damage,
        String location, String original, Integer hiscoreTotal, boolean preview)
    {
        this.id = id;
        this.player = player;
        this.total = total;
        this.killer = killer;
        this.damage = damage;
        this.location = location;
        this.original = original;
        this.hiscoreTotal = hiscoreTotal;
        this.preview = preview;
    }

    boolean hasAbbreviatedTotal()
    {
        return total.endsWith("K") || total.endsWith("k");
    }

    String totalLabel(boolean fullTotals)
    {
        return total + (fullTotals && hasAbbreviatedTotal() && hiscoreTotal != null ? " - " + hiscoreTotal : "");
    }

    String playerLabel(boolean fullTotals)
    {
        return player + " (" + total + ")"
            + (fullTotals && hasAbbreviatedTotal() && hiscoreTotal != null ? " - " + hiscoreTotal : "");
    }

    /** Stable filter fields avoid depending on the visual layout or English sentence order. */
    String filterText()
    {
        return "player=" + player + " total=" + (hiscoreTotal == null ? total : hiscoreTotal)
            + " broadcastTotal=" + total + " damage=" + damage + " killer=" + killer
            + " location=" + location;
    }

    DeathNotice withHiscoreTotal(int level)
    {
        return new DeathNotice(id, player, total, killer, damage, location, original, level, preview);
    }
}
