package bg.parkingprognoza.service;

import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventRepository;
import bg.parkingprognoza.source.EventSource;
import bg.parkingprognoza.source.ScrapedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Pulls events from all sources and stores them, one source at a time. */
@Service
public class EventSyncService {

    private static final Logger log = LoggerFactory.getLogger(EventSyncService.class);

    private final List<EventSource> sources;
    private final EventRepository events;
    private final TransactionTemplate tx;
    private final Clock clock;
    private volatile boolean running;

    public EventSyncService(List<EventSource> sources, EventRepository events, TransactionTemplate tx, Clock clock) {
        this.sources = sources;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Synchronized so the startup sync, the schedule and the admin button never run at once.
     *
     * @return a short per-source report, e.g. {"artvent": "53 events"}
     */
    public synchronized Map<String, String> syncAll() {
        running = true;
        try {
            return doSyncAll();
        } finally {
            running = false;
        }
    }

    /** True while a sync runs; the site shows a notice, which matters on the very first start. */
    public boolean isRunning() {
        return running;
    }

    private Map<String, String> doSyncAll() {
        Map<String, String> report = new LinkedHashMap<>();
        for (EventSource source : sources) {
            try {
                List<ScrapedEvent> scraped = source.fetch();
                int removed = tx.execute(status -> store(source.id(), scraped));
                report.put(source.id(), scraped.size() + " събития" + (removed > 0 ? ", премахнати " + removed : ""));
                log.info("Synced {}: {} events, {} removed", source.id(), scraped.size(), removed);
            } catch (Exception e) {
                // Keep the events we already have; a broken scraper must not wipe the calendar.
                report.put(source.id(), "грешка: " + e.getMessage());
                log.error("Sync of {} failed", source.id(), e);
            }
        }
        return report;
    }

    private int store(String sourceId, List<ScrapedEvent> scraped) {
        Instant now = Instant.now(clock);
        for (ScrapedEvent s : scraped) {
            Event e = events.findBySourceAndExternalId(sourceId, s.externalId())
                    .orElseGet(() -> new Event(sourceId, s.externalId()));
            e.setTitle(truncate(s.title(), 300));
            e.setCategory(s.category());
            e.setStartsAt(s.startsAt());
            e.setEndsAt(s.endsAt());
            e.setVenue(truncate(s.venue(), 300));
            e.setUrl(truncate(s.url(), 1000));
            e.setTicketStatus(s.ticketStatus());
            e.setTicketUrl(truncate(s.ticketUrl(), 1000));
            e.setUpdatedAt(now);
            events.save(e);
        }
        // Future events no longer listed by the source were most likely cancelled or moved.
        Set<String> seen = scraped.stream().map(ScrapedEvent::externalId).collect(Collectors.toSet());
        List<Event> gone = events.findBySourceAndStartsAtGreaterThanEqual(sourceId, LocalDateTime.now(clock)).stream()
                .filter(e -> !seen.contains(e.getExternalId()))
                .toList();
        events.deleteAll(gone);
        return gone.size();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
