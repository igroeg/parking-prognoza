package bg.parkingprognoza.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DailyJobs {

    private final EventSyncService sync;
    private final NotificationService notifications;

    public DailyJobs(EventSyncService sync, NotificationService notifications) {
        this.sync = sync;
        this.notifications = notifications;
    }

    /** In the background, so the site is usable while the first sync (about a minute) runs. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        Thread.ofVirtual().name("startup-sync").start(sync::syncAll);
    }

    @Scheduled(cron = "${app.jobs.sync-cron:0 0 6,18 * * *}", zone = "Europe/Sofia")
    public void sync() {
        sync.syncAll();
    }

    /** Syncs first, so a match announced overnight is included in the morning mail. */
    @Scheduled(cron = "${app.jobs.notify-cron:0 30 7 * * *}", zone = "Europe/Sofia")
    public void notifySubscribers() {
        sync.syncAll();
        notifications.notifyAll(false);
    }
}
