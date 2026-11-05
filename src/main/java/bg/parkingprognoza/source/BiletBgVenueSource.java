package bg.parkingprognoza.source;

import bg.parkingprognoza.config.AppProperties;
import bg.parkingprognoza.model.TicketStatus;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads events from bilet.bg venue pages (by default the Vasil Levski National
 * Stadium). The venue page lists event links; each event page carries a
 * schema.org Event in JSON-LD with the exact start time.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.sources.bilet-bg.enabled", havingValue = "true", matchIfMissing = true)
public class BiletBgVenueSource implements EventSource {

    private static final String BASE_URL = "https://bilet.bg";
    private static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");
    private static final Logger log = LoggerFactory.getLogger(BiletBgVenueSource.class);

    private final HtmlFetcher fetcher;
    private final List<String> venueUrls;
    private final JsonMapper json = JsonMapper.builder().build();

    public BiletBgVenueSource(HtmlFetcher fetcher, AppProperties props) {
        this.fetcher = fetcher;
        this.venueUrls = props.sources().biletBg().venueUrls();
    }

    @Override
    public String id() {
        return "bilet.bg";
    }

    @Override
    public List<ScrapedEvent> fetch() throws IOException {
        List<ScrapedEvent> result = new ArrayList<>();
        for (String venueUrl : venueUrls) {
            for (String eventUrl : eventLinks(fetcher.get(venueUrl))) {
                politePause();
                parseEventPage(fetcher.get(eventUrl), eventUrl).ifPresent(result::add);
            }
        }
        return result;
    }

    Set<String> eventLinks(Document venuePage) {
        Set<String> links = new LinkedHashSet<>();
        for (Element a : venuePage.select("a[href*=/bg/events/]")) {
            String href = a.attr("href");
            links.add(href.startsWith("http") ? href : BASE_URL + href);
        }
        return links;
    }

    Optional<ScrapedEvent> parseEventPage(Document page, String url) {
        for (Element script : page.select("script[type=application/ld+json]")) {
            try {
                Optional<ScrapedEvent> event = findEvent(json.readTree(script.data()), url);
                if (event.isPresent()) {
                    ScrapedEvent e = event.get();
                    return Optional.of(new ScrapedEvent(e.externalId(), e.title(), e.category(), e.startsAt(),
                            e.endsAt(), e.venue(), e.url(), ticketStatus(page), url));
                }
            } catch (JacksonException | DateTimeParseException e) {
                log.warn("Could not read JSON-LD on {}", url, e);
            }
        }
        log.warn("No schema.org Event found on {}", url);
        return Optional.empty();
    }

    private Optional<ScrapedEvent> findEvent(JsonNode node, String url) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                Optional<ScrapedEvent> e = findEvent(item, url);
                if (e.isPresent()) return e;
            }
            return Optional.empty();
        }
        if (node.has("@graph")) {
            return findEvent(node.get("@graph"), url);
        }
        String type = node.path("@type").asString("");
        if (!type.endsWith("Event") || !node.hasNonNull("startDate")) {
            return Optional.empty();
        }
        String title = node.path("name").asString("").trim();
        LocalDateTime start = SchemaOrgDates.parse(node.path("startDate").asString());
        LocalDateTime end = node.hasNonNull("endDate") ? SchemaOrgDates.parse(node.path("endDate").asString()) : null;
        String venue = node.path("location").path("name").asString(null);
        String slug = url.substring(url.lastIndexOf('/') + 1);
        return Optional.of(new ScrapedEvent(slug, title, CategoryGuesser.forStadium(title), start, end, venue, url));
    }

    /**
     * bilet.bg has no availability in its JSON-LD, so read the visible page text
     * (Jsoup's text() skips scripts, where the UI strings for all states live).
     */
    static TicketStatus ticketStatus(Document page) {
        String text = page.body() == null ? "" : page.body().text().toLowerCase();
        if (text.contains("няма налични билети") || text.contains("разпродаден") || text.contains("sold out")) {
            return TicketStatus.SOLD_OUT;
        }
        if (text.contains("купи")) {
            return TicketStatus.AVAILABLE;
        }
        return TicketStatus.UNKNOWN;
    }

    private static void politePause() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
