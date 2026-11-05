package bg.parkingprognoza.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "subscriber")
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    /** Secret token used in confirmation, preference and unsubscribe links. */
    @Column(nullable = false, unique = true, length = 36)
    private String token = UUID.randomUUID().toString();

    private boolean confirmed;

    /** Send a mail on the morning of the event day. */
    private boolean notifySameDay = true;

    /** Send a mail the day before the event. */
    private boolean notifyDayBefore;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "subscriber_category")
    @Column(name = "category", length = 20)
    private Set<EventCategory> categories = EnumSet.allOf(EventCategory.class);

    /** Last day each mail was sent, so a re-run on the same day does not mail twice. */
    private LocalDate lastMorningMailOn;
    private LocalDate lastEveningMailOn;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Subscriber() {
    }

    public Subscriber(String email) {
        this.email = email;
    }

    public boolean wants(EventCategory category) {
        return categories.contains(category);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getToken() { return token; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public boolean isNotifySameDay() { return notifySameDay; }
    public void setNotifySameDay(boolean notifySameDay) { this.notifySameDay = notifySameDay; }
    public boolean isNotifyDayBefore() { return notifyDayBefore; }
    public void setNotifyDayBefore(boolean notifyDayBefore) { this.notifyDayBefore = notifyDayBefore; }
    public Set<EventCategory> getCategories() { return categories; }
    public void setCategories(Set<EventCategory> categories) {
        this.categories = categories.isEmpty() ? EnumSet.noneOf(EventCategory.class) : EnumSet.copyOf(categories);
    }
    public LocalDate getLastMorningMailOn() { return lastMorningMailOn; }
    public void setLastMorningMailOn(LocalDate day) { this.lastMorningMailOn = day; }
    public LocalDate getLastEveningMailOn() { return lastEveningMailOn; }
    public void setLastEveningMailOn(LocalDate day) { this.lastEveningMailOn = day; }
    public Instant getCreatedAt() { return createdAt; }
}
