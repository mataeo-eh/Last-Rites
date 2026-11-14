package com.lastrites;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Compiled user regex exclusions using the JDK's standard Pattern API. */
final class NoticeFilters
{
    private final List<Pattern> patterns = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    NoticeFilters(String text)
    {
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++)
        {
            if (i >= 32)
            {
                errors.add("Only the first 32 filter lines are used.");
                break;
            }
            if (lines[i].isBlank()) { continue; }
            if (lines[i].length() > 512)
            {
                errors.add("Filter line " + (i + 1) + " exceeds the limit (32 rules, 512 characters each).");
                continue;
            }
            try
            {
                patterns.add(Pattern.compile(lines[i], Pattern.CASE_INSENSITIVE));
            }
            catch (PatternSyntaxException ex)
            {
                errors.add("Filter line " + (i + 1) + ": " + ex.getDescription());
            }
        }
    }

    boolean excludes(DeathNotice notice)
    {
        for (Pattern pattern : patterns)
        {
            if (pattern.matcher(notice.original).find() || pattern.matcher(notice.filterText()).find())
            {
                return true;
            }
        }
        return false;
    }

    String errorText() { return String.join("\n", errors); }
}
