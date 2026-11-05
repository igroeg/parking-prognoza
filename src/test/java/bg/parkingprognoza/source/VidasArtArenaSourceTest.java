package bg.parkingprognoza.source;

import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.TicketStatus;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VidasArtArenaSourceTest {

    @Test
    void parsesCards() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/vidas-results.html")) {
            List<ScrapedEvent> events = VidasArtArenaSource.parseResults(
                    Jsoup.parse(in, "UTF-8", "https://vidasartarena.bg/wp-admin/admin-ajax.php"));

            assertThat(events).hasSize(3);
            ScrapedEvent soen = events.get(0);
            assertThat(soen.title()).isEqualTo("SOEN");
            assertThat(soen.startsAt()).isEqualTo(LocalDateTime.of(2027, 3, 25, 19, 0));
            assertThat(soen.category()).isEqualTo(EventCategory.CONCERT);
            assertThat(soen.externalId()).isEqualTo("soen-vidas-art-arena-25-03-2027");
            assertThat(soen.ticketStatus()).isEqualTo(TicketStatus.UNKNOWN);

            ScrapedEvent bruce = events.get(1);
            assertThat(bruce.title()).isEqualTo("Bruce Dickinson – PsychoGod Tour 2027 (часът не е обявен)");
            assertThat(bruce.startsAt()).isEqualTo(LocalDateTime.of(2027, 6, 28, 20, 0));

            assertThat(events.get(2).ticketStatus()).isEqualTo(TicketStatus.SOLD_OUT);
            assertThat(events.get(2).startsAt()).isEqualTo(LocalDateTime.of(2027, 8, 2, 18, 30));
        }
    }

    @Test
    void readsNonceFromEventsPage() throws IOException {
        var page = Jsoup.parse("<script>var fd=new FormData();\n        fd.append('action','festsync_filter');\n"
                + "        fd.append('nonce','dd9ae531cc');</script>");
        assertThat(VidasArtArenaSource.nonce(page)).isEqualTo("dd9ae531cc");
        assertThatThrownBy(() -> VidasArtArenaSource.nonce(Jsoup.parse("<p>changed</p>"))).isInstanceOf(IOException.class);
    }
}
