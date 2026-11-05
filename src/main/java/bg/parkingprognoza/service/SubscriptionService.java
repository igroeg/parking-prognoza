package bg.parkingprognoza.service;

import bg.parkingprognoza.config.AppProperties;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.Subscriber;
import bg.parkingprognoza.model.SubscriberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Double opt-in subscriptions: nobody gets mail until they click the link we sent. */
@Service
public class SubscriptionService {

    private final SubscriberRepository subscribers;
    private final MailService mail;
    private final AppProperties props;

    public SubscriptionService(SubscriberRepository subscribers, MailService mail, AppProperties props) {
        this.subscribers = subscribers;
        this.mail = mail;
        this.props = props;
    }

    public record Preferences(boolean notifySameDay, boolean notifyDayBefore, Set<EventCategory> categories) {
    }

    /**
     * Always behaves the same towards the caller, so the form does not reveal
     * whether an address is already subscribed.
     */
    @Transactional
    public void subscribe(String rawEmail, Preferences prefs) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<Subscriber> existing = subscribers.findByEmailIgnoreCase(email);
        if (existing.isPresent() && existing.get().isConfirmed()) {
            Subscriber s = existing.get();
            mail.send(s.getEmail(), "Вашият абонамент за събития на стадиона", "manage",
                    Map.of("prefsUrl", prefsUrl(s)), null);
            return;
        }
        Subscriber s = existing.orElseGet(() -> new Subscriber(email));
        apply(s, prefs);
        subscribers.save(s);
        mail.send(s.getEmail(), "Потвърдете абонамента за събития на стадиона", "confirm",
                Map.of("confirmUrl", props.trimmedBaseUrl() + "/confirm/" + s.getToken()), null);
    }

    @Transactional
    public Optional<Subscriber> confirm(String token) {
        return subscribers.findByToken(token).map(s -> {
            s.setConfirmed(true);
            return s;
        });
    }

    public Optional<Subscriber> find(String token) {
        return subscribers.findByToken(token);
    }

    @Transactional
    public Optional<Subscriber> update(String token, Preferences prefs) {
        return subscribers.findByToken(token).map(s -> {
            apply(s, prefs);
            return s;
        });
    }

    @Transactional
    public boolean unsubscribe(String token) {
        return subscribers.findByToken(token).map(s -> {
            subscribers.delete(s);
            return true;
        }).orElse(false);
    }

    public String prefsUrl(Subscriber s) {
        return props.trimmedBaseUrl() + "/preferences/" + s.getToken();
    }

    public String unsubscribeUrl(Subscriber s) {
        return props.trimmedBaseUrl() + "/unsubscribe/" + s.getToken();
    }

    private static void apply(Subscriber s, Preferences prefs) {
        s.setNotifySameDay(prefs.notifySameDay());
        s.setNotifyDayBefore(prefs.notifyDayBefore());
        s.setCategories(prefs.categories());
    }
}
