package bg.parkingprognoza.source;

import bg.parkingprognoza.config.AppProperties;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.TicketStatus;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Events from go-sofia.com venue pages. Each venue page embeds a schema.org Place
 * with all its events in JSON-LD, including the ticket link (often Eventim) and
 * {@code offers.availability}, so one request per venue is enough. The visible
 * cards carry a genre tag ("Футбол", "Концерт", ...) used for the category.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.sources.go-sofia.enabled", havingValue = "true", matchIfMissing = true)
public class GoSofiaVenueSource implements EventSource {

    private static final String BASE_URL = "https://www.go-sofia.com";
    private static final Logger log = LoggerFactory.getLogger(GoSofiaVenueSource.class);

    private final HtmlFetcher fetcher;
    private final List<String> venueUrls;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public GoSofiaVenueSource(HtmlFetcher fetcher, AppProperties props, Clock clock) {
        this.fetcher = fetcher;
        this.venueUrls = props.sources().goSofia().venueUrls();
        this.clock = clock;
    }

    @Override
    public String id() {
        return "go-sofia";
    }

    @Override
    public List<ScrapedEvent> fetch() throws IOException {
        List<ScrapedEvent> result = new ArrayList<>();
        for (String url : venueUrls) {
            result.addAll(parseVenuePage(fetcher.get(url), LocalDateTime.now(clock)));
        }
        return result;
    }

    List<ScrapedEvent> parseVenuePage(Document page, LocalDateTime now) throws IOException {
        Map<String, String> tags = tagsByEventUrl(page);
        List<ScrapedEvent> result = new ArrayList<>();
        boolean foundPlace = false;
        for (Element script : page.select("script[type=application/ld+json]")) {
            JsonNode root = json.readTree(script.data());
            for (JsonNode node : root.has("@graph") ? root.get("@graph") : json.createArrayNode().add(root)) {
                if (!"Place".equals(node.path("@type").asString(""))) {
                    continue;
                }
                foundPlace = true;
                for (JsonNode ev : node.path("event")) {
                    try {
                        ScrapedEvent e = toEvent(ev, node.path("name").asString(null), tags);
                        // venue pages keep years-old events; a few hours' grace keeps tonight's match
                        if (e != null && e.startsAt().isAfter(now.minusHours(6))) {
                            result.add(e);
                        }
                    } catch (DateTimeParseException e) {
                        log.warn("Skipping go-sofia event with bad date: {}", ev.path("name").asString(""));
                    }
                }
            }
        }
        if (!foundPlace) {
            throw new IOException("No schema.org Place on go-sofia page " + page.location());
        }
        return result;
    }

    private static ScrapedEvent toEvent(JsonNode ev, String placeName, Map<String, String> tags) {
        String status = ev.path("eventStatus").asString("");
        if (status.endsWith("EventCancelled")) {
            return null;
        }
        String url = ev.path("url").asString("");
        String title = ev.path("name").asString("").trim();
        String venue = ev.path("location").path("name").asString(placeName);
        String id = url.substring(url.lastIndexOf('/') + 1);
        JsonNode offers = ev.path("offers").isArray() ? ev.path("offers").path(0) : ev.path("offers");
        String startDate = ev.path("startDate").asString();
        LocalDateTime start = SchemaOrgDates.parse(startDate);
        if (startDate.length() == 10) {
            // date only: midnight would be misleading, evening is the usual start
            start = start.toLocalDate().atTime(20, 0);
            title += " (часът не е обявен)";
        }
        return new ScrapedEvent(id, title, category(tags.get(url), venue, title), start, null,
                venue == null ? null : venue.replace('\'', '"'), url,
                TicketStatus.fromSchemaOrg(offers.path("availability").asString(null)),
                offers.path("url").asString(null));
    }

    private static Map<String, String> tagsByEventUrl(Document page) {
        Map<String, String> tags = new HashMap<>();
        for (Element card : page.select("a.gs-card[href]")) {
            Element tag = card.selectFirst(".gs-tag");
            if (tag != null) {
                tags.put(BASE_URL + card.attr("href"), tag.text());
            }
        }
        return tags;
    }

    static EventCategory category(String tag, String venue, String title) {
        String t = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
        if (t.contains("футбол") || t.contains("спорт")) return EventCategory.MATCH;
        if (t.contains("театър") || t.contains("драма") || t.contains("комедия") || t.contains("мюзикъл")) {
            return EventCategory.THEATRE;
        }
        if (t.contains("концерт") || t.contains("музика") || t.contains("рок") || t.contains("поп")
                || t.contains("джаз") || t.contains("фестивал") || t.contains("метъл") || t.contains("рап")) {
            return EventCategory.CONCERT;
        }
        boolean stadium = venue != null && venue.toLowerCase(Locale.ROOT).contains("стадион");
        return stadium ? CategoryGuesser.forStadium(title) : EventCategory.OTHER;
    }
}
