package bg.parkingprognoza.model;

/** Ticket availability as reported by the source. Ordered from least to most informative. */
public enum TicketStatus {
    UNKNOWN(""),
    AVAILABLE("Има билети"),
    LIMITED("Последни билети"),
    SOLD_OUT("Разпродадено");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    /** Maps a schema.org ItemAvailability URL such as https://schema.org/SoldOut. */
    public static TicketStatus fromSchemaOrg(String availability) {
        if (availability == null || availability.isBlank()) {
            return UNKNOWN;
        }
        String a = availability.substring(availability.lastIndexOf('/') + 1);
        return switch (a) {
            case "InStock", "OnlineOnly", "InStoreOnly", "PreOrder", "PreSale" -> AVAILABLE;
            case "LimitedAvailability" -> LIMITED;
            case "SoldOut", "OutOfStock", "Discontinued" -> SOLD_OUT;
            default -> UNKNOWN;
        };
    }
}
