package bg.parkingprognoza.source;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** Thin wrapper around Jsoup so sources can be tested without the network. */
@Component
public class HtmlFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ParkingPrognoza/1.0; event reminder for residents)";

    public Document get(String url) throws IOException {
        return connect(url).get();
    }

    /** Form POST, e.g. to a WordPress admin-ajax endpoint that a site's own page calls. */
    public Document post(String url, Map<String, String> form) throws IOException {
        return connect(url).data(form).post();
    }

    private static Connection connect(String url) {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "bg,en;q=0.8")
                .timeout(30_000)
                .maxBodySize(10 * 1024 * 1024);
    }
}
