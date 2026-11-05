package bg.parkingprognoza.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        /* Public URL of the site, used in e-mail links and the calendar feed. */
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue Mail mail,
        @DefaultValue Admin admin,
        @DefaultValue Sources sources) {

    public record Mail(@DefaultValue("Паркинг прогноза <no-reply@localhost>") String from) {
    }

    public record Admin(@DefaultValue("admin") String username, String password) {
    }

    public record Sources(@DefaultValue BiletBg biletBg, @DefaultValue Football football, @DefaultValue GoSofia goSofia) {
    }

    /** go-sofia.com venue pages (it also lists Eventim events, with ticket availability). */
    public record GoSofia(
            @DefaultValue("https://www.go-sofia.com/venues/stadion-vasil-levski") List<String> venueUrls) {
    }

    /**
     * @param teams         FotMob teams as "id:slug" (from fotmob.com/teams/{id}/fixtures/{slug});
     *                      all their upcoming matches are checked
     * @param venueKeywords a match is kept if its stadium name contains one of these
     */
    public record Football(
            @DefaultValue({"10144:cska-sofia", "8632:levski-sofia", "10150:bulgaria"}) List<String> teams,
            @DefaultValue({"Vasil Levski", "Armiya", "Armija", "Armia", "Васил Левски", "Армия"}) List<String> venueKeywords) {
    }

    public record BiletBg(
            @DefaultValue("https://bilet.bg/bg/venues/vassil-levski-national-stadium-640") List<String> venueUrls) {
    }

    public String trimmedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
