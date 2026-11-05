package bg.parkingprognoza.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    public static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");
    public static final Locale BG = Locale.forLanguageTag("bg");

    @Bean
    Clock clock() {
        return Clock.system(SOFIA);
    }

    @Bean
    LocaleResolver localeResolver() {
        return new FixedLocaleResolver(BG);
    }
}
