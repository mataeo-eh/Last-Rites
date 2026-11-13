package com.lastrites;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/** Parses observed server formats only. Unrecognised messages produce no notification. */
final class BroadcastParser
{
    private static final String VICTIM = "(.+?)\\s*\\((\\d{1,4}|\\d(?:\\.\\d+)?[kK])\\)";
    private static final Pattern KILLED = Pattern.compile(
        "^(.+?) killed " + VICTIM + " with an? (\\d+) damage hit(?: in (.+?))?[.!]?$");
    private static final Pattern DIED = Pattern.compile(
        "^" + VICTIM + " was killed by an? (\\d+) damage hit(?: from (.+?))?(?: in (.+?))?[.!]?$");

    private BroadcastParser() { }

    static Optional<DeathNotice> parse(String message, long id)
    {
        if (message == null || message.length() > 2048)
        {
            return Optional.empty();
        }
        // RuneLite's accessor removes game formatting. No player-chat messages enter here.
        String plain = Text.removeFormattingTags(Text.removeTags(message)).replace('\u00a0', ' ').trim();
        Matcher killed = KILLED.matcher(plain);
        Matcher died = DIED.matcher(plain);
        try
        {
            if (killed.matches())
            {
                return Optional.of(new DeathNotice(id, killed.group(2).trim(), killed.group(3),
                    killed.group(1).trim(), Integer.parseInt(killed.group(4)),
                    value(killed.group(5)), plain, null, false));
            }
            if (died.matches())
            {
                // A missing killer stays empty. Location alone cannot prove a cause of death.
                return Optional.of(new DeathNotice(id, died.group(1).trim(), died.group(2),
                    value(died.group(4)), Integer.parseInt(died.group(3)),
                    value(died.group(5)), plain, null, false));
            }
        }
        catch (NumberFormatException ignored)
        {
            // A changed/invalid server field must not break the client event thread.
        }
        return Optional.empty();
    }

    private static String value(String text) { return text == null ? "" : text.trim(); }
}
