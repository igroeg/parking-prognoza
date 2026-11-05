package bg.parkingprognoza.web;

import bg.parkingprognoza.model.EventCategory;
import bg.parkingprognoza.model.Subscriber;
import bg.parkingprognoza.service.SubscriptionService.Preferences;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.EnumSet;
import java.util.Set;

public class SubscribeForm {

    @NotBlank(message = "Въведете имейл")
    @Email(message = "Невалиден имейл")
    @Size(max = 320)
    private String email;

    private boolean notifySameDay = true;
    private boolean notifyDayBefore;
    private Set<EventCategory> categories = EnumSet.allOf(EventCategory.class);

    public static SubscribeForm of(Subscriber s) {
        SubscribeForm f = new SubscribeForm();
        f.email = s.getEmail();
        f.notifySameDay = s.isNotifySameDay();
        f.notifyDayBefore = s.isNotifyDayBefore();
        f.categories = s.getCategories().isEmpty() ? EnumSet.noneOf(EventCategory.class) : EnumSet.copyOf(s.getCategories());
        return f;
    }

    public Preferences toPreferences() {
        return new Preferences(notifySameDay, notifyDayBefore,
                categories == null ? EnumSet.noneOf(EventCategory.class) : categories);
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isNotifySameDay() { return notifySameDay; }
    public void setNotifySameDay(boolean notifySameDay) { this.notifySameDay = notifySameDay; }
    public boolean isNotifyDayBefore() { return notifyDayBefore; }
    public void setNotifyDayBefore(boolean notifyDayBefore) { this.notifyDayBefore = notifyDayBefore; }
    public Set<EventCategory> getCategories() { return categories; }
    public void setCategories(Set<EventCategory> categories) { this.categories = categories; }
}
