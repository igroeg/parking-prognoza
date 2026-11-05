package bg.parkingprognoza.source;

import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.TicketStatus;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtventSourceTest {

    private final ArtventSource source = new ArtventSource(null, Clock.systemDefaultZone());

    @Test
    void parsesShowsAndSkipsOtherCities() throws Exception {
        try (InputStream html = getClass().getResourceAsStream("/fixtures/artvent.html")) {
            List<ScrapedEvent> events = source.parse(Jsoup.parse(html, "UTF-8", "https://artvent.bg/"),
                    LocalDate.of(2026, 11, 5));

            assertThat(events).hasSize(2);
            ScrapedEvent first = events.get(0);
            assertThat(first.title()).isEqualTo("Чамкория");
            assertThat(first.startsAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 19, 0));
            assertThat(first.category()).isEqualTo(EventCategory.THEATRE);
            assertThat(first.url()).isEqualTo("https://artvent.bg/event/chamokria");
            assertThat(first.externalId()).isEqualTo("chamokria@2026-11-10T19:00");

            // January is next year
            assertThat(events.get(1).startsAt()).isEqualTo(LocalDateTime.of(2027, 1, 29, 19, 30));

            assertThat(first.ticketStatus()).isEqualTo(TicketStatus.SOLD_OUT);
            assertThat(events.get(1).ticketStatus()).isEqualTo(TicketStatus.AVAILABLE);
        }
    }

    @Test
    void yearInference() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertThat(ArtventSource.inferYear(5, 11, today)).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(ArtventSource.inferYear(1, 10, today)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ArtventSource.inferYear(15, 2, today)).isEqualTo(LocalDate.of(2027, 2, 15));
    }
}
