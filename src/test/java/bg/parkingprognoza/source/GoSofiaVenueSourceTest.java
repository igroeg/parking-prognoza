package bg.parkingprognoza.source;

import bg.parkingprognoza.config.AppProperties;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.TicketStatus;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoSofiaVenueSourceTest {

    private final GoSofiaVenueSource source = new GoSofiaVenueSource(null, new AppProperties(
            "http://localhost", new AppProperties.Mail("x"), new AppProperties.Admin("a", "b"),
            new AppProperties.Sources(null, null, new AppProperties.GoSofia(List.of()))), Clock.systemDefaultZone());

    @Test
    void readsUpcomingEventsWithTickets() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/go-sofia-venue.html")) {
            List<ScrapedEvent> events = source.parseVenuePage(
                    Jsoup.parse(in, "UTF-8", "https://www.go-sofia.com/venues/stadion-vasil-levski"),
                    LocalDateTime.of(2026, 11, 5, 21, 0));

            // old and cancelled events are dropped
            assertThat(events).extracting(ScrapedEvent::title).containsExactly("ЛЕВСКИ - ЦСКА", "Big Band Tour", "Date only (часът не е обявен)");
            assertThat(events.get(2).startsAt()).isEqualTo(LocalDateTime.of(2027, 8, 2, 20, 0));

            ScrapedEvent derby = events.get(0);
            assertThat(derby.startsAt()).isEqualTo(LocalDateTime.of(2026, 11, 8, 17, 0));
            assertThat(derby.category()).isEqualTo(EventCategory.MATCH);
            assertThat(derby.ticketStatus()).isEqualTo(TicketStatus.AVAILABLE);
            assertThat(derby.ticketUrl()).isEqualTo("https://www.eventim.bg/event/levski-cska-22362375/");
            assertThat(derby.venue()).isEqualTo("Национален стадион \"Васил Левски\"");
            assertThat(derby.externalId()).isEqualTo("5407");

            ScrapedEvent concert = events.get(1);
            assertThat(concert.category()).isEqualTo(EventCategory.CONCERT);
            assertThat(concert.ticketStatus()).isEqualTo(TicketStatus.SOLD_OUT);
        }
    }

    @Test
    void schemaOrgAvailability() {
        assertThat(TicketStatus.fromSchemaOrg("https://schema.org/InStock")).isEqualTo(TicketStatus.AVAILABLE);
        assertThat(TicketStatus.fromSchemaOrg("http://schema.org/LimitedAvailability")).isEqualTo(TicketStatus.LIMITED);
        assertThat(TicketStatus.fromSchemaOrg("SoldOut")).isEqualTo(TicketStatus.SOLD_OUT);
        assertThat(TicketStatus.fromSchemaOrg(null)).isEqualTo(TicketStatus.UNKNOWN);
    }
}
