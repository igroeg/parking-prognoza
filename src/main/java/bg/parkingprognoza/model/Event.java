package bg.parkingprognoza.model;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A single event (match, concert, performance) near the stadium.
 * Times are local to Europe/Sofia.
 */
@Entity
@Table(name = "stadium_event",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source", "externalId"}),
        indexes = @Index(columnList = "startsAt"))
public class Event {

    public static final String MANUAL_SOURCE = "manual";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source id, e.g. "artvent", "bilet.bg" or {@link #MANUAL_SOURCE}. */
    @Column(nullable = false, length = 50)
    private String source;

    /** Stable id within the source, used for de-duplication between runs. */
    @Column(nullable = false, length = 300)
    private String externalId;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @Column(length = 300)
    private String venue;

    @Column(length = 1000)
    private String url;

    @Column(nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketStatus ticketStatus;

    /** Where to buy tickets (or see that they are sold out). */
    @Column(length = 1000)
    private String ticketUrl;

    /** Filled in when duplicates from several sources are merged for display; never stored. */
    @Transient
    private TicketStatus mergedTicketStatus;
    @Transient
    private String mergedTicketUrl;

    protected Event() {
    }

    public Event(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    public boolean isManual() {
        return MANUAL_SOURCE.equals(source);
    }

    /** End time, falling back to a typical duration for the category. */
    public LocalDateTime effectiveEnd() {
        if (endsAt != null && endsAt.isAfter(startsAt)) {
            return endsAt;
        }
        Duration typical = switch (category) {
            case MATCH, THEATRE -> Duration.ofHours(2);
            case CONCERT -> Duration.ofHours(3);
            case OTHER -> Duration.ofHours(2);
        };
        return startsAt.plus(typical);
    }

    /** Best known ticket status, including information from merged duplicates. */
    public TicketStatus getTicket() {
        if (mergedTicketStatus != null) return mergedTicketStatus;
        return ticketStatus == null ? TicketStatus.UNKNOWN : ticketStatus;
    }

    public String getTicketLink() {
        return mergedTicketUrl != null ? mergedTicketUrl : ticketUrl;
    }

    void mergeTicketInfo(TicketStatus status, String url) {
        this.mergedTicketStatus = status;
        this.mergedTicketUrl = url;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public EventCategory getCategory() { return category; }
    public void setCategory(EventCategory category) { this.category = category; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public TicketStatus getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(TicketStatus ticketStatus) { this.ticketStatus = ticketStatus; }
    public String getTicketUrl() { return ticketUrl; }
    public void setTicketUrl(String ticketUrl) { this.ticketUrl = ticketUrl; }
}
