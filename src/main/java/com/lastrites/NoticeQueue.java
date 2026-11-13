package com.lastrites;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Bounded, in-memory queue shared by the client event, HTTP callback, and render threads. */
@Singleton
final class NoticeQueue
{
    private final Clock clock;
    private final Deque<DeathNotice> waiting = new ArrayDeque<>();
    private final Deque<DeathNotice> history = new ArrayDeque<>();
    private DeathNotice active;
    private long expires;
    private NoticeFilters filters = new NoticeFilters("");

    @Inject NoticeQueue() { this(Clock.systemUTC()); }
    NoticeQueue(Clock clock) { this.clock = clock; }

    synchronized void setFilters(String text)
    {
        filters = new NoticeFilters(text);
        waiting.removeIf(filters::excludes);
        if (active != null && !active.preview && filters.excludes(active)) { active = null; }
    }

    synchronized String filterErrors() { return filters.errorText(); }

    synchronized boolean add(DeathNotice notice)
    {
        if (!notice.preview && filters.excludes(notice)) { return false; }
        // The most recent announcements remain useful during busy broadcasts.
        if (waiting.size() >= 25) { waiting.removeFirst(); }
        waiting.addLast(notice);
        if (!notice.preview)
        {
            history.addFirst(notice);
            while (history.size() > 50) { history.removeLast(); }
        }
        return true;
    }

    synchronized DeathNotice current(int seconds, boolean positionPreview)
    {
        if (active != null && clock.millis() >= expires) { active = null; }
        if (active == null && !waiting.isEmpty())
        {
            active = waiting.removeFirst();
            expires = clock.millis() + seconds * 1000L;
        }
        return active == null && positionPreview ? sample() : active;
    }

    synchronized void enrich(long id, int total)
    {
        if (total <= 0) { return; }
        if (active != null && active.id == id)
        {
            active = active.withHiscoreTotal(total);
            if (filters.excludes(active)) { active = null; }
        }
        List<DeathNotice> updated = new ArrayList<>();
        for (DeathNotice notice : waiting)
        {
            DeathNotice next = notice.id == id ? notice.withHiscoreTotal(total) : notice;
            if (!filters.excludes(next)) { updated.add(next); }
        }
        waiting.clear();
        waiting.addAll(updated);
        List<DeathNotice> records = new ArrayList<>();
        for (DeathNotice notice : history)
        {
            records.add(notice.id == id ? notice.withHiscoreTotal(total) : notice);
        }
        history.clear();
        history.addAll(records);
    }

    synchronized List<DeathNotice> history() { return new ArrayList<>(history); }
    synchronized void clear() { active = null; waiting.clear(); history.clear(); }

    static DeathNotice sample()
    {
        return new DeathNotice(-1, "Fallen Adventurer", "1.1K", "Hill Giant", 12,
            "Asgarnia", "Preview only", 1142, true);
    }
}
