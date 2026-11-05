package bg.parkingprognoza.source;

import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.TicketStatus;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the programme of Theatre Artvent (Aula Maxima, UACEG, near the stadium).
 * The page is server-rendered; each show is a {@code .cd-timeline-block} with a
 * "dd.MM" date (no year) and an "HH:mm" time.
 */
@Component
@Order(5)
@ConditionalOnProperty(name = "app.sources.artvent.enabled", havingValue = "true", matchIfMissing = true)
public class ArtventSource implements EventSource {

    static final String PROGRAMME_URL = "https://artvent.bg/teatar/artvent/programa";
    private static final String BASE_URL = "https://artvent.bg";
    private static final Pattern DAY_MONTH = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Logger log = LoggerFactory.getLogger(ArtventSource.class);

    private final HtmlFetcher fetcher;
    private final Clock clock;

    public ArtventSource(HtmlFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "artvent";
    }

    @Override
    public List<ScrapedEvent> fetch() throws IOException {
        return parse(fetcher.get(PROGRAMME_URL), LocalDate.now(clock));
    }

    List<ScrapedEvent> parse(Document doc, LocalDate today) {
        List<ScrapedEvent> result = new ArrayList<>();
        for (Element block : doc.select(".cd-timeline-block")) {
            try {
                ScrapedEvent event = parseBlock(block, today);
                if (event != null) {
                    result.add(event);
                }
            } catch (DateTimeException | IllegalStateException e) {
                log.warn("Skipping Artvent entry that could not be parsed: {}", block.text(), e);
            }
        }
        return result;
    }

    private ScrapedEvent parseBlock(Element block, LocalDate today) {
        Element dateEl = block.selectFirst(".cd-timeline-img");
        Element titleEl = block.selectFirst(".cd-item-info h2");
        Element whenWhere = block.selectFirst(".cd-date");
        if (dateEl == null || titleEl == null || whenWhere == null) {
            return null;
        }
        String location = whenWhere.text();
        if (!location.toUpperCase().contains("ARTVENT")) {
            // Guest performances in other cities do not affect parking here.
            return null;
        }
        Matcher dm = DAY_MONTH.matcher(dateEl.text());
        Element timeEl = whenWhere.selectFirst("strong");
        Matcher tm = TIME.matcher(timeEl != null ? timeEl.text() : location);
        if (!dm.find() || !tm.find()) {
            return null;
        }
        LocalDate date = inferYear(Integer.parseInt(dm.group(1)), Integer.parseInt(dm.group(2)), today);
        LocalDateTime start = date.atTime(LocalTime.of(Integer.parseInt(tm.group(1)), Integer.parseInt(tm.group(2))));

        Element link = block.selectFirst("a[href*=/event/]");
        String href = link != null ? link.attr("href") : null;
        String url = href == null ? PROGRAMME_URL : href.startsWith("http") ? href : BASE_URL + href;
        String slug = href == null ? titleEl.text() : href.substring(href.lastIndexOf('/') + 1);

        return new ScrapedEvent(slug + "@" + start, titleEl.text().trim(), EventCategory.THEATRE,
                start, null, "Театър Artvent (Аула „Максима“, УАСГ)", url, ticketStatus(block), url);
    }

    /** A sold-out show has a "Разпродадено" button instead of the ticket.bg widget. */
    static TicketStatus ticketStatus(Element block) {
        Element info = block.selectFirst(".cd-item-info");
        if (info == null) {
            return TicketStatus.UNKNOWN;
        }
        if (info.select("a, button").stream().anyMatch(b -> b.text().toLowerCase().contains("разпродад"))) {
            return TicketStatus.SOLD_OUT;
        }
        if (!info.select("script[src*=ticket], a[href*=ticket], [data-slug]").isEmpty()) {
            return TicketStatus.AVAILABLE;
        }
        return TicketStatus.UNKNOWN;
    }

    /** The page shows only day and month; pick the nearest year that is not clearly in the past. */
    static LocalDate inferYear(int day, int month, LocalDate today) {
        LocalDate candidate = LocalDate.of(today.getYear(), month, day);
        if (candidate.isBefore(today.minusMonths(2))) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }
}
