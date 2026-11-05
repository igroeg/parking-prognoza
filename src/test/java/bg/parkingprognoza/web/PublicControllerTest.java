package bg.parkingprognoza.web;

import bg.parkingprognoza.model.Event;
import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.EventRepository;
import bg.parkingprognoza.model.SubscriberRepository;
import bg.parkingprognoza.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PublicControllerTest {

    @Autowired MockMvc mvc;
    @Autowired SubscriberRepository subscribers;
    @Autowired EventRepository events;
    @Autowired Clock clock;
    @MockitoBean MailService mail;

    @Test
    void homePageRenders() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Предстоящи събития")));
    }

    @Test
    void majorEventsAreHighlightedAndFilterable() throws Exception {
        events.deleteAll();
        event("Левски – ЦСКА", EventCategory.MATCH, 3);
        event("Чамкория", EventCategory.THEATRE, 1);

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Следващи мачове и концерти")))
                .andExpect(content().string(containsString("Чамкория")));
        mvc.perform(get("/").param("show", "MAJOR"))
                .andExpect(content().string(containsString("Левски – ЦСКА")))
                .andExpect(content().string(not(containsString("Чамкория"))));
        mvc.perform(get("/").param("show", "THEATRE"))
                .andExpect(content().string(containsString("Чамкория")))
                // still in the highlight card, but not in the list
                .andExpect(content().string(containsString("Левски – ЦСКА")));
        events.deleteAll();
    }

    private void event(String title, EventCategory category, int inDays) {
        Event e = new Event(Event.MANUAL_SOURCE, title);
        e.setTitle(title);
        e.setCategory(category);
        e.setStartsAt(LocalDate.now(clock).plusDays(inDays).atTime(19, 0));
        e.setUpdatedAt(Instant.now());
        events.save(e);
    }

    @Test
    void subscribeConfirmAndUnsubscribe() throws Exception {
        mvc.perform(post("/subscribe").with(csrf())
                        .param("email", "Neighbour@Example.com")
                        .param("notifySameDay", "true")
                        .param("categories", "MATCH", "CONCERT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Проверете пощата")));
        verify(mail).send(eq("neighbour@example.com"), any(), eq("confirm"), any(), any());

        var s = subscribers.findByEmailIgnoreCase("neighbour@example.com").orElseThrow();
        assertThat(s.isConfirmed()).isFalse();

        mvc.perform(get("/confirm/" + s.getToken()))
                .andExpect(redirectedUrl("/preferences/" + s.getToken() + "?confirmed"));
        assertThat(subscribers.findByToken(s.getToken()).orElseThrow().isConfirmed()).isTrue();

        // one-click unsubscribe (RFC 8058) posts without a CSRF token
        mvc.perform(post("/unsubscribe/" + s.getToken())).andExpect(status().isOk());
        assertThat(subscribers.findByToken(s.getToken())).isEmpty();
    }

    @Test
    void invalidEmailShowsError() throws Exception {
        mvc.perform(post("/subscribe").with(csrf()).param("email", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Невалиден имейл")));
    }

    @Test
    void calendarFeedIsServed() throws Exception {
        mvc.perform(get("/calendar.ics").param("c", "MATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/calendar")))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")));
    }

    @Test
    void adminRequiresLogin() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isUnauthorized());
    }
}
