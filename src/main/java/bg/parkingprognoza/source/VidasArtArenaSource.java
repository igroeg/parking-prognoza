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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vidas Art Arena (open-air arena at the Kolodrum, Borisova gradina, next to the stadium).
 * Its /events/ page loads the list through the site's own public filter endpoint
 * (admin-ajax.php, action festsync_filter) with a nonce embedded in the page, one
 * year at a time; we do the same for this year and next. The site shows no ticket
 * availability (tickets go through Fest Club), so the status stays unknown unless a
 * card is marked sold out.
 */
@Component
@Order(4)
@ConditionalOnProperty(name = "app.sources.vidas.enabled", havingValue = "true", matchIfMissing = true)
public class VidasArtArenaSource implements EventSource {

    static final String EVENTS_URL = "https://vidasartarena.bg/events/";
    private static final String AJAX_URL = "https://vidasartarena.bg/wp-admin/admin-ajax.php";
    private static final String VENUE = "Vidas Art Arena (Колодрум, Борисова градина)";
    private static final Pattern NONCE = Pattern.compile("'action'\\s*,\\s*'festsync_filter'\\s*\\);\\s*fd\\.append\\('nonce'\\s*,\\s*'([a-zA-Z0-9]+)'");
    private static final Pattern TIME = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern YEAR = Pattern.compile("(\\d{4})");
    /** "SOEN @ Vidas Art Arena, 25.03.2027" -> "SOEN" */
    private static final Pattern VENUE_SUFFIX = Pattern.compile("(?i)\\s*[@|]\\s*Vidas Art Arena.*$");
    private static final List<String> MONTHS = List.of("яну", "фев", "мар", "апр", "май", "юни", "юли", "авг", "сеп", "окт", "ное", "дек");
    private static final Logger log = LoggerFactory.getLogger(VidasArtArenaSource.class);

    private final HtmlFetcher fetcher;
    private final Clock clock;

    public VidasArtArenaSource(HtmlFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "vidas";
    }

    @Override
    public List<ScrapedEvent> fetch() throws IOException {
        String nonce = nonce(fetcher.get(EVENTS_URL));
        int year = LocalDate.now(clock).getYear();
        List<ScrapedEvent> result = new ArrayList<>();
        for (int y = year; y <= year + 1; y++) {
            Document page = fetcher.post(AJAX_URL, Map.of(
                    "action", "festsync_filter", "nonce", nonce, "tab", "upcoming",
                    "year", String.valueOf(y), "type", "", "venue", "", "search", ""));
            String body = page.body() == null ? "" : page.body().text().trim();
            if (body.equals("0") || body.equals("-1")) {
                throw new IOException("Vidas Art Arena filter rejected the request (nonce?): " + body);
            }
            result.addAll(parseResults(page));
        }
        return result;
    }

    static String nonce(Document eventsPage) throws IOException {
        Matcher m = NONCE.matcher(eventsPage.html());
        if (!m.find()) {
            throw new IOException("No festsync_filter nonce on " + EVENTS_URL + " (page changed?)");
        }
        return m.group(1);
    }

    static List<ScrapedEvent> parseResults(Document page) {
        List<ScrapedEvent> result = new ArrayList<>();
        for (Element card : page.select("a.fs-card[href]")) {
            try {
                ScrapedEvent e = parseCard(card);
                if (e != null) result.add(e);
            } catch (DateTimeException | IllegalArgumentException e) {
                log.warn("Skipping Vidas Art Arena card: {}", card.text(), e);
            }
        }
        return result;
    }

    private static ScrapedEvent parseCard(Element card) {
        Element dayEl = card.selectFirst(".fs-card__day");
        Element monthYearEl = card.selectFirst(".fs-card__monthyear");
        Element titleEl = card.selectFirst(".fs-card__title");
        if (dayEl == null || monthYearEl == null || titleEl == null) {
            return null;
        }
        String monthYear = monthYearEl.text().toLowerCase();
        int month = MONTHS.indexOf(MONTHS.stream().filter(monthYear::startsWith).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown month: " + monthYear))) + 1;
        Matcher y = YEAR.matcher(monthYear);
        if (!y.find()) {
            throw new IllegalArgumentException("No year in: " + monthYear);
        }
        LocalDate date = LocalDate.of(Integer.parseInt(y.group(1)), month, Integer.parseInt(dayEl.text().trim()));

        String title = VENUE_SUFFIX.matcher(titleEl.text().trim()).replaceFirst("");
        LocalTime time = null;
        for (Element meta : card.select(".fs-meta-item")) {
            Matcher t = TIME.matcher(meta.text());
            if (t.find()) {
                time = LocalTime.of(Integer.parseInt(t.group(1)), Integer.parseInt(t.group(2)));
                break;
            }
        }
        if (time == null) {
            time = LocalTime.of(20, 0);
            title += " (часът не е обявен)";
        }

        String href = card.attr("abs:href").isEmpty() ? card.attr("href") : card.attr("abs:href");
        String slug = href.replaceAll("/+$", "");
        slug = slug.substring(slug.lastIndexOf('/') + 1);
        String marker = (card.className() + " " + card.text()).toLowerCase();
        TicketStatus tickets = marker.contains("sold") || marker.contains("разпродад")
                ? TicketStatus.SOLD_OUT : TicketStatus.UNKNOWN;

        return new ScrapedEvent(slug, title, EventCategory.CONCERT, LocalDateTime.of(date, time), null,
                VENUE, href, tickets, tickets.isKnown() ? href : null);
    }
}
