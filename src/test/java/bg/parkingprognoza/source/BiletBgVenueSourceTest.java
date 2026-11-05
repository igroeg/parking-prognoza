package bg.parkingprognoza.source;

import bg.parkingprognoza.config.AppProperties;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.TicketStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BiletBgVenueSourceTest {

    private final BiletBgVenueSource source = new BiletBgVenueSource(null, new AppProperties(
            "http://localhost", new AppProperties.Mail("x"), new AppProperties.Admin("a", "b"),
            new AppProperties.Sources(new AppProperties.BiletBg(List.of("https://bilet.bg/bg/venues/x")), null, null)));

    @Test
    void collectsUniqueEventLinks() throws IOException {
        assertThat(source.eventLinks(load("bilet-venue.html"))).containsExactly(
                "https://bilet.bg/bg/events/bulgaria-ispania-9200",
                "https://bilet.bg/bg/events/kontsert-9201");
    }

    @Test
    void readsJsonLdEvent() throws IOException {
        String url = "https://bilet.bg/bg/events/bulgaria-ispania-9200";
        ScrapedEvent e = source.parseEventPage(load("bilet-event.html"), url).orElseThrow();
        assertThat(e.title()).isEqualTo("България vs. Испания");
        assertThat(e.startsAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 21, 45));
        assertThat(e.category()).isEqualTo(EventCategory.MATCH);
        assertThat(e.venue()).isEqualTo("Национален Стадион \"Васил Левски\"");
        assertThat(e.externalId()).isEqualTo("bulgaria-ispania-9200");
    }

    @Test
    void ticketStatusFromVisibleText() {
        // the "no tickets" UI string is always present inside a script; only visible text counts
        assertThat(BiletBgVenueSource.ticketStatus(Jsoup.parse(
                "<body><script>{\"no_tickets_available\":\"Няма налични билети за това събитие.\"}</script>"
                        + "<button>Купи</button></body>"))).isEqualTo(TicketStatus.AVAILABLE);
        assertThat(BiletBgVenueSource.ticketStatus(Jsoup.parse(
                "<body><p>Няма налични билети за това събитие.</p></body>"))).isEqualTo(TicketStatus.SOLD_OUT);
        assertThat(BiletBgVenueSource.ticketStatus(Jsoup.parse("<body><p>Info</p></body>")))
                .isEqualTo(TicketStatus.UNKNOWN);
    }

    @Test
    void parsesDateFormats() {
        assertThat(SchemaOrgDates.parse("2026-06-20T20:00"))
                .isEqualTo(LocalDateTime.of(2026, 6, 20, 20, 0));
        // 17:00 UTC in summer is 20:00 in Sofia
        assertThat(SchemaOrgDates.parse("2026-06-20T17:00:00Z"))
                .isEqualTo(LocalDateTime.of(2026, 6, 20, 20, 0));
        assertThat(SchemaOrgDates.parse("2026-06-20"))
                .isEqualTo(LocalDateTime.of(2026, 6, 20, 0, 0));
    }

    @Test
    void guessesCategory() {
        assertThat(CategoryGuesser.forStadium("Левски срещу ЦСКА")).isEqualTo(EventCategory.MATCH);
        assertThat(CategoryGuesser.forStadium("Финал за Купа на България")).isEqualTo(EventCategory.MATCH);
        assertThat(CategoryGuesser.forStadium("Metallica - M72 World Tour")).isEqualTo(EventCategory.CONCERT);
        assertThat(CategoryGuesser.forStadium("Мачкам и пея")).isEqualTo(EventCategory.CONCERT);
    }

    private Document load(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return Jsoup.parse(in, "UTF-8", "https://bilet.bg/");
        }
    }
}
