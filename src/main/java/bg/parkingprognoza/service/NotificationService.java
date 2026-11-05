package bg.parkingprognoza.service;

import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.Subscriber;
import bg.parkingprognoza.model.SubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Sends each confirmed subscriber one digest per day if anything is on today/tomorrow. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SubscriberRepository subscribers;
    private final EventQueryService events;
    private final SubscriptionService subscriptions;
    private final MailService mail;
    private final Clock clock;

    public NotificationService(SubscriberRepository subscribers, EventQueryService events,
                               SubscriptionService subscriptions, MailService mail, Clock clock) {
        this.subscribers = subscribers;
        this.events = events;
        this.subscriptions = subscriptions;
        this.mail = mail;
        this.clock = clock;
    }

    /** @return number of mails sent */
    public int notifyAll(boolean force) {
        LocalDate today = LocalDate.now(clock);
        List<Event> todays = eventsOn(today);
        List<Event> tomorrows = eventsOn(today.plusDays(1));
        int sent = 0;
        for (Subscriber s : subscribers.findByConfirmedTrue()) {
            if (!force && today.equals(s.getLastNotifiedOn())) {
                continue;
            }
            List<Event> forToday = s.isNotifySameDay() ? filter(todays, s) : List.of();
            List<Event> forTomorrow = s.isNotifyDayBefore() ? filter(tomorrows, s) : List.of();
            if (forToday.isEmpty() && forTomorrow.isEmpty()) {
                continue;
            }
            try {
                mail.send(s.getEmail(), subject(forToday, forTomorrow), "digest", Map.of(
                        "today", forToday,
                        "tomorrow", forTomorrow,
                        "prefsUrl", subscriptions.prefsUrl(s),
                        "unsubscribeUrl", subscriptions.unsubscribeUrl(s)), subscriptions.unsubscribeUrl(s));
                s.setLastNotifiedOn(today);
                subscribers.save(s);
                sent++;
            } catch (RuntimeException e) {
                log.error("Could not notify subscriber {}", s.getId(), e);
            }
        }
        log.info("Daily notification: {} mails sent ({} events today, {} tomorrow)", sent, todays.size(), tomorrows.size());
        return sent;
    }

    private List<Event> eventsOn(LocalDate day) {
        return events.between(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    private static List<Event> filter(List<Event> list, Subscriber s) {
        return list.stream().filter(e -> s.wants(e.getCategory())).toList();
    }

    static String subject(List<Event> today, List<Event> tomorrow) {
        if (!today.isEmpty()) {
            return "Днес до стадиона: " + titles(today) + " – трудно паркиране";
        }
        return "Утре до стадиона: " + titles(tomorrow);
    }

    private static String titles(List<Event> list) {
        String joined = list.stream().map(Event::getTitle).collect(Collectors.joining(", "));
        return joined.length() > 90 ? joined.substring(0, 87) + "..." : joined;
    }
}
