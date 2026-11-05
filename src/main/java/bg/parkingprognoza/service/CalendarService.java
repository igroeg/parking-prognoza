package bg.parkingprognoza.service;

import bg.parkingprognoza.config.AppConfig;
import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.TicketStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Builds an iCalendar (RFC 5545) feed. Calendar apps subscribe to its URL and
 * re-download it periodically, so new events show up without any action.
 */
@Service
public class CalendarService {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final EventQueryService events;
    private final Clock clock;

    public CalendarService(EventQueryService events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public String feed(Set<EventCategory> categories, boolean alarms, String baseUrl) {
        List<Event> list = events.from(LocalDateTime.now(clock).minusDays(30))
                .stream().filter(e -> categories.contains(e.getCategory())).toList();
        return render(list, alarms, baseUrl, Instant.now(clock));
    }

    static String render(List<Event> list, boolean alarms, String baseUrl, Instant now) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Parking Prognoza//BG");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:Паркинг прогноза – стадион „Васил Левски“");
        line(ics, "X-WR-TIMEZONE:Europe/Sofia");
        line(ics, "REFRESH-INTERVAL;VALUE=DURATION:PT6H");
        line(ics, "X-PUBLISHED-TTL:PT6H");
        for (Event e : list) {
            line(ics, "BEGIN:VEVENT");
            line(ics, "UID:" + uid(e, baseUrl));
            line(ics, "DTSTAMP:" + UTC.format(now));
            if (e.getUpdatedAt() != null) {
                line(ics, "LAST-MODIFIED:" + UTC.format(e.getUpdatedAt()));
            }
            line(ics, "DTSTART:" + utc(e.getStartsAt()));
            line(ics, "DTEND:" + utc(e.effectiveEnd()));
            line(ics, "SUMMARY:" + escape(e.getCategory().getIcon() + " " + e.getTitle()
                    + (e.getTicket() == TicketStatus.SOLD_OUT ? " (разпродадено)" : "")));
            if (e.getVenue() != null) {
                line(ics, "LOCATION:" + escape(e.getVenue()));
            }
            line(ics, "DESCRIPTION:" + escape(e.getCategory().getLabel()
                    + " – очаквайте трудно паркиране около стадиона."
                    + (e.getTicket().isKnown() ? "\nБилети: " + e.getTicket().getLabel() : "")
                    + (e.getUrl() != null ? "\n" + e.getUrl() : "")));
            if (e.getUrl() != null) {
                line(ics, "URL:" + e.getUrl());
            }
            line(ics, "CATEGORIES:" + escape(e.getCategory().getLabel()));
            line(ics, "TRANSP:TRANSPARENT");
            if (alarms) {
                alarm(ics, "-P1D", "Утре: " + e.getTitle());
                alarm(ics, "-PT3H", "След 3 часа: " + e.getTitle());
            }
            line(ics, "END:VEVENT");
        }
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private static void alarm(StringBuilder ics, String trigger, String text) {
        line(ics, "BEGIN:VALARM");
        line(ics, "ACTION:DISPLAY");
        line(ics, "TRIGGER:" + trigger);
        line(ics, "DESCRIPTION:" + escape(text));
        line(ics, "END:VALARM");
    }

    private static String uid(Event e, String baseUrl) {
        String host = baseUrl.replaceFirst("^https?://", "").replaceFirst("[:/].*$", "");
        return (e.getSource() + "-" + e.getExternalId()).replaceAll("[^A-Za-z0-9._@-]", "_") + "@" + host;
    }

    private static String utc(LocalDateTime local) {
        return UTC.format(local.atZone(AppConfig.SOFIA).toInstant());
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    /** Appends a content line, folded at 75 octets without splitting UTF-8 characters. */
    static void line(StringBuilder out, String content) {
        int octets = 0;
        int limit = 75;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + len > limit) {
                out.append("\r\n ");
                octets = 1;
                limit = 75;
            }
            out.appendCodePoint(cp);
            octets += len;
            i += Character.charCount(cp);
        }
        out.append("\r\n");
    }
}
