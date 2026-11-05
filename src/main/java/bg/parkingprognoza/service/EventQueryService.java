package bg.parkingprognoza.service;

import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventMerger;
import bg.parkingprognoza.model.EventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** Read side used by the site, mails and calendar: upcoming events with duplicates merged. */
@Service
public class EventQueryService {

    private final EventRepository events;

    public EventQueryService(EventRepository events) {
        this.events = events;
    }

    public List<Event> from(LocalDateTime from) {
        return EventMerger.merge(events.findByStartsAtGreaterThanEqualOrderByStartsAt(from));
    }

    public List<Event> between(LocalDateTime from, LocalDateTime to) {
        return EventMerger.merge(events.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(from, to));
    }
}
