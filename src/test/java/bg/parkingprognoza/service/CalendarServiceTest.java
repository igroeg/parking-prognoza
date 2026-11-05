package bg.parkingprognoza.service;

import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventCategory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarServiceTest {

    @Test
    void rendersEventInUtcWithAlarms() {
        Event e = new Event("bilet.bg", "bulgaria-ispania-9200");
        e.setTitle("България, Испания; квалификация");
        e.setCategory(EventCategory.MATCH);
        e.setStartsAt(LocalDateTime.of(2026, 11, 14, 21, 45));
        e.setVenue("НС „Васил Левски“");
        e.setUpdatedAt(Instant.parse("2026-11-05T08:00:00Z"));

        String ics = CalendarService.render(List.of(e), true, "https://example.com", Instant.parse("2026-11-05T09:00:00Z"));

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        // Sofia is UTC+2 in November
        assertThat(ics).contains("DTSTART:20261114T194500Z\r\n", "DTEND:20261114T214500Z\r\n");
        assertThat(ics).contains("SUMMARY:⚽ България\\, Испания\\; квалификация");
        assertThat(ics).contains("UID:bilet.bg-bulgaria-ispania-9200@example.com");
        assertThat(ics).contains("TRIGGER:-P1D", "TRIGGER:-PT3H");

        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
    }

    @Test
    void foldsLongLinesWithoutBreakingCharacters() {
        StringBuilder out = new StringBuilder();
        CalendarService.line(out, "DESCRIPTION:" + "ъ".repeat(100));
        String unfolded = out.toString().replace("\r\n ", "");
        assertThat(unfolded).isEqualTo("DESCRIPTION:" + "ъ".repeat(100) + "\r\n");
    }
}
