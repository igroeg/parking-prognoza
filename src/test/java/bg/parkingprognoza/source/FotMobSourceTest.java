package bg.parkingprognoza.source;

import bg.parkingprognoza.config.AppProperties;
import bg.parkingprognoza.model.EventCategory;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FotMobSourceTest {

    private final FotMobSource source = new FotMobSource(null, new AppProperties(
            "http://localhost", new AppProperties.Mail("x"), new AppProperties.Admin("a", "b"),
            new AppProperties.Sources(null, new AppProperties.Football(
                    List.of("10144:cska-sofia"), List.of("Vasil Levski", "Armiya")), null)));

    @Test
    void keepsOnlyUpcomingFixtures() throws IOException {
        var data = JsonMapper.builder().build().readTree(resource("fotmob-team.json"));
        List<FotMobSource.Fixture> fixtures = source.upcomingFixtures(data, 10144);

        assertThat(fixtures).extracting(FotMobSource.Fixture::matchId).containsExactly("6112542", "5760530", "5760612");
        FotMobSource.Fixture trabzon = fixtures.get(0);
        assertThat(trabzon.away()).isEqualTo("Trabzonspor");
        assertThat(trabzon.pageUrl()).isEqualTo("https://www.fotmob.com/matches/trabzonspor-vs-cska-sofia/39unf8");
        assertThat(fixtures.get(2).dateTbd()).isTrue();
    }

    @Test
    void readsStadiumFromMatchPage() throws IOException {
        var page = Jsoup.parse(resource("fotmob-match.html"), "https://www.fotmob.com/");
        var data = source.nextData(page);
        assertThat(source.stadium(data, "6112542"))
                .contains(new FotMobSource.Stadium("Stadion Vasil Levski", "Sofia"));
        // a page for a different match must not be trusted
        assertThatThrownBy(() -> source.stadium(data, "5760484")).isInstanceOf(IOException.class);
    }

    @Test
    void nearbyStadiums() {
        assertThat(source.isNearby(new FotMobSource.Stadium("Stadion Vasil Levski", "Sofia"))).isTrue();
        assertThat(source.isNearby(new FotMobSource.Stadium("Bulgarska Armiya", ""))).isTrue();
        assertThat(source.isNearby(new FotMobSource.Stadium("Stadion Hristo Botev", "Plovdiv"))).isFalse();
        assertThat(source.isNearby(new FotMobSource.Stadium("Stadion Vasil Levski", "Pleven"))).isFalse();
        assertThat(source.isNearby(new FotMobSource.Stadium("Stadion Georgi Asparuhov", "Sofia"))).isFalse();
    }

    @Test
    void buildsEventInSofiaTime() {
        var f = new FotMobSource.Fixture("6112542", "CSKA Sofia", "Trabzonspor", "Conference League",
                Instant.parse("2026-11-26T20:00:00Z"), false, false, "https://www.fotmob.com/matches/x");
        ScrapedEvent e = FotMobSource.toEvent(f, new FotMobSource.Stadium("Stadion Vasil Levski", "Sofia"));

        assertThat(e.title()).isEqualTo("ЦСКА – Trabzonspor (Conference League)");
        assertThat(e.startsAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 22, 0));
        assertThat(e.venue()).isEqualTo("НС „Васил Левски“");
        assertThat(e.category()).isEqualTo(EventCategory.MATCH);
    }

    private String resource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
