package bg.parkingprognoza.source;

import bg.parkingprognoza.config.AppConfig;
import bg.parkingprognoza.config.AppProperties;
import bg.parkingprognoza.model.EventCategory;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Football matches from FotMob team pages. A team's fixture list has no venue,
 * so for each upcoming match the match page is read for its stadium; only
 * matches at a stadium matching {@code venueKeywords} are kept. The same team
 * plays at different stadiums (e.g. CSKA: league at Bulgarska Armiya, European
 * games at Vasil Levski; Bulgaria sometimes in Plovdiv), hence the per-match check.
 *
 * <p>Both pages embed their data as JSON in {@code <script id="__NEXT_DATA__">}.
 * About one request per upcoming match per run; run it at most a few times a day.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.sources.football.enabled", havingValue = "true", matchIfMissing = true)
public class FotMobSource implements EventSource {

    private static final String BASE_URL = "https://www.fotmob.com";
    private static final Logger log = LoggerFactory.getLogger(FotMobSource.class);

    /** Bulgarian names for the teams people here care about; others stay as FotMob writes them. */
    private static final Map<String, String> TEAM_NAMES = Map.of(
            "CSKA Sofia", "ЦСКА",
            "Levski Sofia", "Левски",
            "Bulgaria", "България",
            "CSKA 1948", "ЦСКА 1948",
            "Slavia Sofia", "Славия",
            "Septemvri Sofia", "Септември",
            "PFC Lokomotiv Sofia 1929", "Локомотив София");

    private final HtmlFetcher fetcher;
    private final List<String> teams;
    private final List<String> venueKeywords;
    private final JsonMapper json = JsonMapper.builder().build();

    public FotMobSource(HtmlFetcher fetcher, AppProperties props) {
        this.fetcher = fetcher;
        this.teams = props.sources().football().teams();
        this.venueKeywords = props.sources().football().venueKeywords();
    }

    /** An upcoming match from a team's fixture list. */
    record Fixture(String matchId, String home, String away, String tournament, Instant kickoff,
                   boolean timeTbd, boolean dateTbd, String pageUrl) {
    }

    record Stadium(String name, String city) {
    }

    @Override
    public String id() {
        return "football";
    }

    @Override
    public List<ScrapedEvent> fetch() throws IOException {
        // keyed by match id: a derby shows up in both teams' lists
        Map<String, Fixture> fixtures = new LinkedHashMap<>();
        for (String team : teams) {
            String[] idAndSlug = team.split(":", 2);
            String url = BASE_URL + "/teams/" + idAndSlug[0] + "/fixtures/" + (idAndSlug.length > 1 ? idAndSlug[1] : "");
            for (Fixture f : upcomingFixtures(nextData(fetcher.get(url)), Long.parseLong(idAndSlug[0]))) {
                fixtures.putIfAbsent(f.matchId(), f);
            }
            pause();
        }

        List<ScrapedEvent> result = new ArrayList<>();
        for (Fixture f : fixtures.values()) {
            // /match/{id} serves exactly this match; the slug URL in pageUrl may serve the other
            // fixture between the same two teams (the "#id" part only matters in the browser).
            Optional<Stadium> stadium = stadium(nextData(fetcher.get(BASE_URL + "/match/" + f.matchId())), f.matchId());
            pause();
            if (stadium.isPresent() && isNearby(stadium.get())) {
                result.add(toEvent(f, stadium.get()));
            } else {
                log.debug("Skipping {} - {}: stadium {}", f.home(), f.away(), stadium.map(Stadium::name).orElse("unknown"));
            }
        }
        return result;
    }

    JsonNode nextData(Document page) throws IOException {
        Element script = page.selectFirst("script#__NEXT_DATA__");
        if (script == null) {
            throw new IOException("FotMob page has no __NEXT_DATA__ (layout changed or request blocked): " + page.location());
        }
        return json.readTree(script.data());
    }

    List<Fixture> upcomingFixtures(JsonNode nextData, long teamId) throws IOException {
        JsonNode fallback = nextData.path("props").path("pageProps").path("fallback");
        JsonNode list = fallback.path("team-" + teamId).path("fixtures").path("allFixtures").path("fixtures");
        if (!list.isArray()) {
            throw new IOException("No fixture list for FotMob team " + teamId);
        }
        List<Fixture> result = new ArrayList<>();
        for (JsonNode f : list) {
            JsonNode status = f.path("status");
            boolean upcoming = !status.path("started").asBoolean(false)
                    && !status.path("finished").asBoolean(false)
                    && !status.path("cancelled").asBoolean(false);
            // away games count too: e.g. Septemvri hosts Levski at Vasil Levski
            if (!upcoming) {
                continue;
            }
            String pageUrl = f.path("pageUrl").asString("");
            int hash = pageUrl.indexOf('#');
            result.add(new Fixture(
                    f.path("id").asString(),
                    f.path("home").path("name").asString(""),
                    f.path("away").path("name").asString(""),
                    f.path("tournament").path("name").asString(""),
                    Instant.parse(status.path("utcTime").asString()),
                    status.path("matchTimeTbd").asBoolean(false),
                    status.path("matchDateTbd").asBoolean(false),
                    BASE_URL + (hash >= 0 ? pageUrl.substring(0, hash) : pageUrl)));
        }
        return result;
    }

    Optional<Stadium> stadium(JsonNode nextData, String expectedMatchId) throws IOException {
        JsonNode pageProps = nextData.path("props").path("pageProps");
        String matchId = pageProps.path("general").path("matchId").asString("");
        if (!matchId.equals(expectedMatchId)) {
            throw new IOException("FotMob served match " + matchId + " instead of " + expectedMatchId);
        }
        JsonNode stadium = pageProps.path("content").path("matchFacts").path("infoBox").path("Stadium");
        String name = stadium.path("name").asString("");
        return name.isBlank() ? Optional.empty() : Optional.of(new Stadium(name, stadium.path("city").asString("")));
    }

    boolean isNearby(Stadium stadium) {
        // other towns have their own "Vasil Levski" stadiums
        if (!stadium.city().isBlank() && !stadium.city().toLowerCase(Locale.ROOT).contains("sofia")
                && !stadium.city().toLowerCase(Locale.ROOT).contains("софия")) {
            return false;
        }
        String name = stadium.name().toLowerCase(Locale.ROOT);
        return venueKeywords.stream().anyMatch(k -> name.contains(k.toLowerCase(Locale.ROOT)));
    }

    static ScrapedEvent toEvent(Fixture f, Stadium stadium) {
        LocalDateTime start = LocalDateTime.ofInstant(f.kickoff(), AppConfig.SOFIA);
        StringBuilder title = new StringBuilder(bg(f.home()) + " – " + bg(f.away()));
        if (!f.tournament().isBlank()) {
            title.append(" (").append(f.tournament()).append(")");
        }
        if (f.dateTbd()) {
            title.append(" – датата може да се промени");
        } else if (f.timeTbd()) {
            title.append(" – часът предстои");
        }
        return new ScrapedEvent(f.matchId(), title.toString(), EventCategory.MATCH, start, null,
                venueName(stadium.name()), f.pageUrl());
    }

    static String venueName(String fotmobName) {
        String n = fotmobName.toLowerCase(Locale.ROOT);
        if (n.contains("levski") || n.contains("левски")) return "НС „Васил Левски“";
        if (n.contains("armi") || n.contains("армия")) return "Стадион „Българска армия“";
        return fotmobName;
    }

    private static String bg(String team) {
        return TEAM_NAMES.getOrDefault(team, team);
    }

    private static void pause() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
