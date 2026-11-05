package bg.parkingprognoza.web;

import bg.parkingprognoza.config.AppProperties;
import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.Subscriber;
import bg.parkingprognoza.service.CalendarService;
import bg.parkingprognoza.service.EventQueryService;
import bg.parkingprognoza.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Controller
public class PublicController {

    private final EventQueryService events;
    private final SubscriptionService subscriptions;
    private final CalendarService calendar;
    private final AppProperties props;
    private final Clock clock;

    public PublicController(EventQueryService events, SubscriptionService subscriptions, CalendarService calendar,
                            AppProperties props, Clock clock) {
        this.events = events;
        this.subscriptions = subscriptions;
        this.calendar = calendar;
        this.props = props;
        this.clock = clock;
    }

    @ModelAttribute("allCategories")
    EventCategory[] allCategories() {
        return EventCategory.values();
    }

    /** Which events the home page lists. */
    enum Show {
        ALL, MAJOR, THEATRE;

        boolean includes(Event e) {
            return switch (this) {
                case ALL -> true;
                case MAJOR -> e.getCategory().isMajor();
                case THEATRE -> e.getCategory() == EventCategory.THEATRE;
            };
        }
    }

    @GetMapping("/")
    String home(@RequestParam(defaultValue = "ALL") Show show, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new SubscribeForm());
        }
        LocalDate today = LocalDate.now(clock);
        List<Event> upcoming = events.from(today.atStartOfDay());
        Map<LocalDate, List<Event>> byDay = upcoming.stream().filter(show::includes).collect(Collectors.groupingBy(
                e -> e.getStartsAt().toLocalDate(), TreeMap::new, Collectors.toList()));
        List<Event> nextMajor = upcoming.stream().filter(e -> e.getCategory().isMajor()).limit(5).toList();
        model.addAttribute("byDay", byDay);
        model.addAttribute("nextMajor", nextMajor);
        model.addAttribute("show", show);
        model.addAttribute("majorCount", upcoming.stream().filter(Show.MAJOR::includes).count());
        model.addAttribute("theatreCount", upcoming.stream().filter(Show.THEATRE::includes).count());
        model.addAttribute("allCount", upcoming.size());
        model.addAttribute("today", today);
        model.addAttribute("tomorrow", today.plusDays(1));

        String https = props.trimmedBaseUrl() + "/calendar.ics";
        String webcal = https.replaceFirst("^https?://", "webcal://");
        model.addAttribute("icsUrl", https);
        model.addAttribute("webcalUrl", webcal);
        model.addAttribute("googleUrl", "https://calendar.google.com/calendar/render?cid="
                + URLEncoder.encode(webcal, StandardCharsets.UTF_8));
        return "index";
    }

    @PostMapping("/subscribe")
    String subscribe(@Valid @ModelAttribute("form") SubscribeForm form, BindingResult errors, Model model) {
        if (errors.hasErrors()) {
            return home(Show.ALL, model);
        }
        subscriptions.subscribe(form.getEmail(), form.toPreferences());
        return message(model, "Проверете пощата си",
                "Изпратихме писмо до " + form.getEmail() + ". Натиснете линка в него, за да потвърдите абонамента.");
    }

    @GetMapping("/confirm/{token}")
    String confirm(@PathVariable String token) {
        Subscriber s = subscriptions.confirm(token).orElseThrow(PublicController::notFound);
        return "redirect:/preferences/" + s.getToken() + "?confirmed";
    }

    @GetMapping("/preferences/{token}")
    String preferences(@PathVariable String token, Model model) {
        Subscriber s = subscriptions.find(token).orElseThrow(PublicController::notFound);
        model.addAttribute("form", SubscribeForm.of(s));
        model.addAttribute("token", token);
        return "preferences";
    }

    @PostMapping("/preferences/{token}")
    String savePreferences(@PathVariable String token, @ModelAttribute("form") SubscribeForm form) {
        subscriptions.update(token, form.toPreferences()).orElseThrow(PublicController::notFound);
        return "redirect:/preferences/" + token + "?saved";
    }

    /** Shows a button rather than unsubscribing directly, so link scanners cannot unsubscribe people. */
    @GetMapping("/unsubscribe/{token}")
    String unsubscribePage(@PathVariable String token, Model model) {
        subscriptions.find(token).orElseThrow(PublicController::notFound);
        model.addAttribute("token", token);
        return "unsubscribe";
    }

    @PostMapping("/unsubscribe/{token}")
    String unsubscribe(@PathVariable String token, Model model) {
        subscriptions.unsubscribe(token);
        return message(model, "Отписахте се", "Няма да получавате повече писма от нас.");
    }

    @GetMapping(value = "/calendar.ics")
    ResponseEntity<String> calendar(@RequestParam(name = "c", required = false) Set<EventCategory> categories,
                                    @RequestParam(defaultValue = "true") boolean alarms) {
        Set<EventCategory> selected = categories == null || categories.isEmpty()
                ? EnumSet.allOf(EventCategory.class) : categories;
        String body = calendar.feed(selected, alarms, props.trimmedBaseUrl());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"stadium-events.ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=1800")
                .body(body);
    }

    private static String message(Model model, String title, String text) {
        model.addAttribute("title", title);
        model.addAttribute("text", text);
        return "message";
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Линкът е невалиден или абонаментът вече е прекратен.");
    }
}
