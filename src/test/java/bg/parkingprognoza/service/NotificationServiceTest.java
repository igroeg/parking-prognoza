package bg.parkingprognoza.service;

import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.EventRepository;
import bg.parkingprognoza.model.Subscriber;
import bg.parkingprognoza.model.SubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@SpringBootTest
class NotificationServiceTest {

    @Autowired NotificationService notifications;
    @Autowired EventRepository events;
    @Autowired SubscriberRepository subscribers;
    @Autowired Clock clock;
    @MockitoBean MailService mail;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        subscribers.deleteAll();
    }

    @Test
    void sendsOnlyWhatEachSubscriberAskedFor() {
        LocalDate today = LocalDate.now(clock);
        event("Мач днес", EventCategory.MATCH, today.atTime(20, 0));
        event("Театър утре", EventCategory.THEATRE, today.plusDays(1).atTime(19, 0));

        subscriber("same-day@example.com", true, false, EnumSet.allOf(EventCategory.class));
        subscriber("day-before@example.com", false, true, EnumSet.allOf(EventCategory.class));
        subscriber("theatre-only-today@example.com", true, false, EnumSet.of(EventCategory.THEATRE));

        assertThat(notifications.notifyAll(false)).isEqualTo(2);
        verify(mail).send(eq("same-day@example.com"), startsWith("Днес"), eq("digest"), any(), any());
        verify(mail).send(eq("day-before@example.com"), startsWith("Утре"), eq("digest"), any(), any());
        verify(mail, never()).send(eq("theatre-only-today@example.com"), any(), any(), any(), any());

        // a second run on the same day does not mail again
        assertThat(notifications.notifyAll(false)).isZero();
    }

    @Test
    void unconfirmedSubscribersGetNothing() {
        event("Мач днес", EventCategory.MATCH, LocalDate.now(clock).atTime(20, 0));
        Subscriber s = new Subscriber("pending@example.com");
        subscribers.save(s);

        assertThat(notifications.notifyAll(false)).isZero();
        verify(mail, never()).send(any(), any(), any(), any(), any());
    }

    private void event(String title, EventCategory category, LocalDateTime start) {
        Event e = new Event(Event.MANUAL_SOURCE, title);
        e.setTitle(title);
        e.setCategory(category);
        e.setStartsAt(start);
        e.setUpdatedAt(Instant.now());
        events.save(e);
    }

    private void subscriber(String email, boolean sameDay, boolean dayBefore, EnumSet<EventCategory> categories) {
        Subscriber s = new Subscriber(email);
        s.setConfirmed(true);
        s.setNotifySameDay(sameDay);
        s.setNotifyDayBefore(dayBefore);
        s.setCategories(categories);
        subscribers.save(s);
    }
}
