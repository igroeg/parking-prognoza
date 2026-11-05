package bg.parkingprognoza.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The same event often comes from several sources (a derby from FotMob and from
 * go-sofia/Eventim). Events at the same place and the same minute are treated as
 * one: the entry from the most trusted source is shown, enriched with the best
 * ticket information any of the duplicates has.
 */
public final class EventMerger {

    /** Lower is preferred for title/link. */
    private static final List<String> SOURCE_PRIORITY = List.of(Event.MANUAL_SOURCE, "football", "artvent", "go-sofia", "bilet.bg");

    private EventMerger() {
    }

    public static List<Event> merge(List<Event> events) {
        Map<String, List<Event>> groups = new LinkedHashMap<>();
        for (Event e : events) {
            groups.computeIfAbsent(e.getStartsAt() + "|" + venueKey(e.getVenue()), k -> new ArrayList<>()).add(e);
        }
        List<Event> result = new ArrayList<>(groups.size());
        for (List<Event> group : groups.values()) {
            if (group.size() == 1) {
                result.add(group.getFirst());
                continue;
            }
            Event primary = group.stream().min(Comparator.comparingInt(EventMerger::priority)).orElseThrow();
            Event bestTicket = group.stream().max(Comparator
                    .comparing((Event e) -> e.getTicket().ordinal())
                    .thenComparing(e -> -priority(e))).orElseThrow();
            if (bestTicket.getTicket().isKnown()) {
                primary.mergeTicketInfo(bestTicket.getTicket(), bestTicket.getTicketLink());
            }
            result.add(primary);
        }
        return result;
    }

    private static int priority(Event e) {
        int i = SOURCE_PRIORITY.indexOf(e.getSource());
        return i < 0 ? SOURCE_PRIORITY.size() : i;
    }

    /** Different sources spell venues differently; reduce them to a comparable key. */
    static String venueKey(String venue) {
        if (venue == null) return "";
        String v = venue.toLowerCase(Locale.ROOT);
        if (v.contains("армия") || v.contains("armi")) return "armia";
        if (v.contains("vidas")) return "vidas";
        if (v.contains("artvent") || v.contains("артвент") || v.contains("максима")) return "artvent";
        if ((v.contains("левски") || v.contains("levski")) && !v.contains("аспарухов") && !v.contains("asparuhov")) {
            return "vasil-levski";
        }
        return v.replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
