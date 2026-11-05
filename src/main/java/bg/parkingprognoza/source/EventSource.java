package bg.parkingprognoza.source;

import java.io.IOException;
import java.util.List;

/**
 * A place we read upcoming events from. Implementations should return all
 * upcoming events they know about; events that disappear from a successful
 * fetch are treated as cancelled.
 */
public interface EventSource {

    /** Short stable id stored with each event, e.g. "artvent". */
    String id();

    List<ScrapedEvent> fetch() throws IOException;
}
