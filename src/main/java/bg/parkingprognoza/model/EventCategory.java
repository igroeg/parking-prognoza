package bg.parkingprognoza.model;

public enum EventCategory {
    MATCH("Мач", "⚽", true),
    CONCERT("Концерт", "🎤", true),
    THEATRE("Театър", "🎭", false),
    OTHER("Друго", "📅", true);

    private final String label;
    private final String icon;
    private final boolean major;

    EventCategory(String label, String icon, boolean major) {
        this.label = label;
        this.icon = icon;
        this.major = major;
    }

    /** Tens of thousands of people (stadium) rather than a few hundred (theatre). */
    public boolean isMajor() {
        return major;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
