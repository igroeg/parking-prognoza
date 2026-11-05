package bg.parkingprognoza.source;

import bg.parkingprognoza.model.EventCategory;

import java.util.regex.Pattern;

/** Rough category guess for events at the stadium, based on the title. */
public final class CategoryGuesser {

    private static final Pattern MATCH = Pattern.compile(
            "(?iu)(\\bvs\\.?(?=\\s)|\\bсрещу\\b|\\bмач\\b|футбол|football|купа на българия|"
                    + "квалификац|лига на нациите|nations league|\\bуефа\\b|\\buefa\\b|\\bfifa\\b|\\bфифа\\b)",
            Pattern.UNICODE_CHARACTER_CLASS);

    private CategoryGuesser() {
    }

    public static EventCategory forStadium(String title) {
        return MATCH.matcher(title).find() ? EventCategory.MATCH : EventCategory.CONCERT;
    }
}
