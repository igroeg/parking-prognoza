package bg.parkingprognoza.source;

import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.TicketStatus;

import java.time.LocalDateTime;

/** An event as read from an external source, before it is stored. */
public record ScrapedEvent(
        String externalId,
        String title,
        EventCategory category,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String venue,
        String url,
        TicketStatus ticketStatus,
        String ticketUrl) {

    /** For sources that know nothing about tickets. */
    public ScrapedEvent(String externalId, String title, EventCategory category, LocalDateTime startsAt,
                        LocalDateTime endsAt, String venue, String url) {
        this(externalId, title, category, startsAt, endsAt, venue, url, TicketStatus.UNKNOWN, null);
    }
}
