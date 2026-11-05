package bg.parkingprognoza.service;

import bg.parkingprognoza.service.NotificationService.Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Once a day: sync at 18:00, so the evening mail at 20:00 (about tomorrow) and the
 * morning mail at 07:30 (about today) use fresh data. All times Europe/Sofia.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DailyJobs {

    private static final Logger log = LoggerFactory.getLogger(DailyJobs.class);

    private final EventSyncService sync;
    private final NotificationService notifications;
    private final Duration startupMaxAge;

    public DailyJobs(EventSyncService sync, NotificationService notifications,
                     @Value("${app.jobs.startup-sync-if-older-than:24h}") Duration startupMaxAge) {
        this.sync = sync;
        this.notifications = notifications;
        this.startupMaxAge = startupMaxAge;
    }

    /** Only when the data is missing or old, e.g. first start or after a long downtime; in the background. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartupIfStale() {
        if (sync.isStale(startupMaxAge)) {
            Thread.ofVirtual().name("startup-sync").start(sync::syncAll);
        } else {
            log.info("Events are fresh (synced within {}), skipping startup sync", startupMaxAge);
        }
    }

    @Scheduled(cron = "${app.jobs.sync-cron:0 0 18 * * *}", zone = "Europe/Sofia")
    public void sync() {
        sync.syncAll();
    }

    @Scheduled(cron = "${app.jobs.evening-mail-cron:0 0 20 * * *}", zone = "Europe/Sofia")
    public void eveningMail() {
        notifications.send(Digest.EVENING, false);
    }

    @Scheduled(cron = "${app.jobs.morning-mail-cron:0 30 7 * * *}", zone = "Europe/Sofia")
    public void morningMail() {
        notifications.send(Digest.MORNING, false);
    }
}
