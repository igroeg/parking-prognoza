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

    /** Which daily mail: the morning one is about today, the evening one about tomorrow. */
    public enum Digest {
        MORNING, EVENING
    }

    /**
     * Sends one digest to every confirmed subscriber who wants it and has matching events.
     *
     * @param force send even if this digest already went out today (admin button)
     * @return number of mails sent
     */
    public int send(Digest digest, boolean force) {
        LocalDate today = LocalDate.now(clock);
        boolean morning = digest == Digest.MORNING;
        List<Event> candidates = eventsOn(morning ? today : today.plusDays(1));
        int sent = 0;
        for (Subscriber s : subscribers.findByConfirmedTrue()) {
            boolean wanted = morning ? s.isNotifySameDay() : s.isNotifyDayBefore();
            LocalDate lastSent = morning ? s.getLastMorningMailOn() : s.getLastEveningMailOn();
            if (!wanted || (!force && today.equals(lastSent))) {
                continue;
            }
            List<Event> list = filter(candidates, s);
            if (list.isEmpty()) {
                continue;
            }
            try {
                mail.send(s.getEmail(), subject(digest, list), "digest", Map.of(
                        "today", morning ? list : List.of(),
                        "tomorrow", morning ? List.of() : list,
                        "prefsUrl", subscriptions.prefsUrl(s),
                        "unsubscribeUrl", subscriptions.unsubscribeUrl(s)), subscriptions.unsubscribeUrl(s));
                if (morning) {
                    s.setLastMorningMailOn(today);
                } else {
                    s.setLastEveningMailOn(today);
                }
                subscribers.save(s);
                sent++;
            } catch (RuntimeException e) {
                log.error("Could not send {} mail to subscriber {}", digest, s.getId(), e);
            }
        }
        log.info("{} mail: {} sent ({} events)", digest, sent, candidates.size());
        return sent;
    }

    private List<Event> eventsOn(LocalDate day) {
        return events.between(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    private static List<Event> filter(List<Event> list, Subscriber s) {
        return list.stream().filter(e -> s.wants(e.getCategory())).toList();
    }

    static String subject(Digest digest, List<Event> list) {
        return digest == Digest.MORNING
                ? "Днес до стадиона: " + titles(list) + " – трудно паркиране"
                : "Утре до стадиона: " + titles(list);
    }

    private static String titles(List<Event> list) {
        String joined = list.stream().map(Event::getTitle).collect(Collectors.joining(", "));
        return joined.length() > 90 ? joined.substring(0, 87) + "..." : joined;
    }
}
