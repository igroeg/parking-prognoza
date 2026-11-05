package bg.parkingprognoza.service;

import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventSyncServiceTest {

    @Autowired EventSyncService sync;
    @Autowired EventRepository events;

    @BeforeEach
    void clean() {
        events.deleteAll();
    }

    @Test
    void staleWhenNothingWasEverSynced() {
        assertThat(sync.isStale(Duration.ofHours(24))).isTrue();
        // manual events do not count as a sync
        save(Event.MANUAL_SOURCE, Instant.now());
        assertThat(sync.isStale(Duration.ofHours(24))).isTrue();
    }

    @Test
    void freshAfterRecentSyncStaleAfterADay() {
        save("artvent", Instant.now().minus(Duration.ofHours(2)));
        assertThat(sync.isStale(Duration.ofHours(24))).isFalse();

        events.deleteAll();
        save("artvent", Instant.now().minus(Duration.ofHours(30)));
        assertThat(sync.isStale(Duration.ofHours(24))).isTrue();
    }

    private void save(String source, Instant updatedAt) {
        Event e = new Event(source, source + updatedAt);
        e.setTitle("x");
        e.setCategory(EventCategory.THEATRE);
        e.setStartsAt(LocalDateTime.now().plusDays(3));
        e.setUpdatedAt(updatedAt);
        events.save(e);
    }
}
