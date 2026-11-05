package bg.parkingprognoza.web;

import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.EventRepository;
import bg.parkingprognoza.model.SubscriberRepository;
import bg.parkingprognoza.service.EventSyncService;
import bg.parkingprognoza.service.NotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Manual events (e.g. football matches) and maintenance buttons. Protected by HTTP basic auth. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final EventRepository events;
    private final SubscriberRepository subscribers;
    private final EventSyncService sync;
    private final NotificationService notifications;
    private final Clock clock;

    public AdminController(EventRepository events, SubscriberRepository subscribers, EventSyncService sync,
                           NotificationService notifications, Clock clock) {
        this.events = events;
        this.subscribers = subscribers;
        this.sync = sync;
        this.notifications = notifications;
        this.clock = clock;
    }

    @GetMapping
    String admin(Model model) {
        model.addAttribute("events", events.findByStartsAtGreaterThanEqualOrderByStartsAt(
                LocalDate.now(clock).atStartOfDay()));
        model.addAttribute("subscriberCount", subscribers.count());
        model.addAttribute("confirmedCount", subscribers.findByConfirmedTrue().size());
        model.addAttribute("allCategories", EventCategory.values());
        return "admin";
    }

    @PostMapping("/events")
    String addEvent(@RequestParam String title,
                    @RequestParam EventCategory category,
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
                    @RequestParam(required = false) String venue,
                    @RequestParam(required = false) String url,
                    RedirectAttributes flash) {
        Event e = new Event(Event.MANUAL_SOURCE, UUID.randomUUID().toString());
        e.setTitle(title.trim());
        e.setCategory(category);
        e.setStartsAt(LocalDateTime.of(date, time));
        e.setVenue(venue == null || venue.isBlank() ? "Национален стадион „Васил Левски“" : venue.trim());
        e.setUrl(url == null || url.isBlank() ? null : url.trim());
        e.setUpdatedAt(Instant.now(clock));
        events.save(e);
        flash.addFlashAttribute("info", "Добавено: " + e.getTitle());
        return "redirect:/admin";
    }

    @PostMapping("/events/{id}/delete")
    String deleteEvent(@PathVariable long id, RedirectAttributes flash) {
        events.findById(id).filter(Event::isManual).ifPresent(e -> {
            events.delete(e);
            flash.addFlashAttribute("info", "Изтрито: " + e.getTitle());
        });
        return "redirect:/admin";
    }

    @PostMapping("/sync")
    String syncNow(RedirectAttributes flash) {
        flash.addFlashAttribute("info", "Синхронизация: " + sync.syncAll());
        return "redirect:/admin";
    }

    @PostMapping("/notify")
    String notifyNow(@RequestParam NotificationService.Digest digest, RedirectAttributes flash) {
        int sent = notifications.send(digest, true);
        String which = digest == NotificationService.Digest.MORNING ? "Сутрешни (за днес)" : "Вечерни (за утре)";
        flash.addFlashAttribute("info", which + " писма: изпратени " + sent);
        return "redirect:/admin";
    }
}
