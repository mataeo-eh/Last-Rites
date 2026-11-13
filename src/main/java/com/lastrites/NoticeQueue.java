package com.lastrites;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Session history and popup lifecycle, shared safely by event, render, HTTP, and Swing threads. */
@Singleton
final class NoticeQueue
{
    private final Clock clock;
    private final Deque<DeathNotice> history = new ArrayDeque<>();
    private NoticeFilters filters = new NoticeFilters("");
    private DeathNotice preview;
    private boolean automaticEnabled = true;
    private boolean visible;
    private boolean pinned;
    private int duration = 7;
    private long triggeredAt;
    private long revision;

    @Inject NoticeQueue() { this(Clock.systemUTC()); }
    NoticeQueue(Clock clock) { this.clock = clock; }

    synchronized void configure(int seconds, boolean enabled)
    {
        duration = Math.max(0, Math.min(3600, seconds));
        if (automaticEnabled && !enabled) { hide(); }
        automaticEnabled = enabled;
        expire();
    }

    synchronized void setFilters(String text)
    {
        filters = new NoticeFilters(text);
        history.removeIf(filters::excludes);
        if (preview == null && history.isEmpty() && !pinned) { visible = false; }
        revision++;
    }

    synchronized String filterErrors() { return filters.errorText(); }

    synchronized boolean add(DeathNotice notice)
    {
        if (notice.preview) { showSample(); return true; }
        if (filters.excludes(notice)) { return false; }
        history.addFirst(notice);
        while (history.size() > 50) { history.removeLast(); }
        revision++;
        if (automaticEnabled)
        {
            // Every new death restarts the timed view; a manual Show remains pinned.
            preview = null;
            visible = true;
            triggeredAt = clock.millis();
        }
        return true;
    }

    synchronized void showSample()
    {
        // A sample overrides manual hiding without changing the saved automatic preference.
        preview = sample();
        pinned = false;
        visible = true;
        triggeredAt = clock.millis();
        revision++;
    }

    synchronized void showPinned()
    {
        automaticEnabled = true;
        preview = history.isEmpty() ? sample() : null;
        pinned = true;
        visible = true;
        revision++;
    }

    synchronized void hide()
    {
        automaticEnabled = false;
        visible = false;
        pinned = false;
        preview = null;
        revision++;
    }

    synchronized boolean isVisible() { expire(); return visible; }
    synchronized long revision() { return revision; }

    synchronized List<DeathNotice> visibleNotices()
    {
        expire();
        List<DeathNotice> result = new ArrayList<>();
        if (!visible) { return result; }
        if (preview != null) { result.add(preview); }
        result.addAll(history);
        return result;
    }

    synchronized DeathNotice current()
    {
        List<DeathNotice> rows = visibleNotices();
        return rows.isEmpty() ? null : rows.get(0);
    }

    synchronized void enrich(long id, int total)
    {
        if (total <= 0) { return; }
        List<DeathNotice> records = new ArrayList<>();
        for (DeathNotice notice : history)
        {
            DeathNotice next = notice.id == id ? notice.withHiscoreTotal(total) : notice;
            if (!filters.excludes(next)) { records.add(next); }
        }
        history.clear();
        history.addAll(records);
        if (history.isEmpty() && preview == null && !pinned) { visible = false; }
    }

    synchronized List<DeathNotice> history() { return new ArrayList<>(history); }

    synchronized void clear()
    {
        history.clear();
        preview = null;
        visible = false;
        pinned = false;
        revision++;
    }

    private void expire()
    {
        // Zero is deliberately persistent; only timed automatic/sample displays expire.
        if (visible && !pinned && duration > 0 && clock.millis() - triggeredAt >= duration * 1000L)
        {
            visible = false;
            preview = null;
        }
    }

    static DeathNotice sample()
    {
        return new DeathNotice(-1, "Fallen Adventurer", "1.1K", "Hill Giant", 12,
            "Asgarnia", "Preview only", 1142, true);
    }
}
