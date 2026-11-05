package bg.parkingprognoza.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventMergerTest {

    private static final LocalDateTime DERBY = LocalDateTime.of(2026, 11, 8, 17, 0);

    @Test
    void mergesSameEventFromSeveralSources() {
        Event fotmob = event("football", "Левски – ЦСКА (First Professional League)", DERBY, "НС „Васил Левски“",
                TicketStatus.UNKNOWN, null);
        Event goSofia = event("go-sofia", "ЛЕВСКИ - ЦСКА", DERBY, "Национален стадион \"Васил Левски\"",
                TicketStatus.SOLD_OUT, "https://www.eventim.bg/event/x/");

        List<Event> merged = EventMerger.merge(List.of(fotmob, goSofia));

        assertThat(merged).hasSize(1);
        Event e = merged.getFirst();
        assertThat(e.getTitle()).isEqualTo("Левски – ЦСКА (First Professional League)");
        assertThat(e.getTicket()).isEqualTo(TicketStatus.SOLD_OUT);
        assertThat(e.getTicketLink()).isEqualTo("https://www.eventim.bg/event/x/");
    }

    @Test
    void keepsDifferentVenuesOrTimesApart() {
        Event stadium = event("go-sofia", "Concert", DERBY, "Национален стадион 'Васил Левски'", TicketStatus.UNKNOWN, null);
        Event armia = event("football", "ЦСКА – X", DERBY, "Стадион „Българска армия“", TicketStatus.UNKNOWN, null);
        Event later = event("bilet.bg", "Concert", DERBY.plusHours(2), "Национален Стадион \"Васил Левски\"",
                TicketStatus.UNKNOWN, null);
        Event asparuhov = event("manual", "Левски – Y", DERBY, "Стадион Георги Аспарухов", TicketStatus.UNKNOWN, null);

        assertThat(EventMerger.merge(List.of(stadium, armia, later, asparuhov))).hasSize(4);
    }

    private static Event event(String source, String title, LocalDateTime start, String venue,
                               TicketStatus status, String ticketUrl) {
        Event e = new Event(source, title);
        e.setTitle(title);
        e.setCategory(EventCategory.MATCH);
        e.setStartsAt(start);
        e.setVenue(venue);
        e.setTicketStatus(status);
        e.setTicketUrl(ticketUrl);
        e.setUpdatedAt(Instant.now());
        return e;
    }
}
