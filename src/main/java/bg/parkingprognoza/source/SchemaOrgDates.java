package bg.parkingprognoza.source;

import bg.parkingprognoza.config.AppConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Parses schema.org startDate values into Sofia local time. */
public final class SchemaOrgDates {

    private SchemaOrgDates() {
    }

    /** Accepts "2026-11-08T17:00:00+02:00", "2026-06-20T17:00:00Z", "2026-11-06T20:00" and "2026-11-06". */
    public static LocalDateTime parse(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(AppConfig.SOFIA).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // no offset
        }
        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay();
        }
        return LocalDateTime.parse(value);
    }
}
