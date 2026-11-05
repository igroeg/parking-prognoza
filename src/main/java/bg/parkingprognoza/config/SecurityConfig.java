package bg.parkingprognoza.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.UUID;

/** Everything is public except /admin, which uses HTTP basic auth. */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                // RFC 8058 one-click unsubscribe is a cross-site POST from the mail provider.
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/unsubscribe/**")
                        .csrfTokenRequestHandler(eagerCsrfTokens()));
        return http.build();
    }

    /**
     * Load the CSRF token at the start of every request. The default lazy loading
     * creates the session while the page is rendered, which fails once a large page
     * has already started streaming to the browser.
     */
    private static XorCsrfTokenRequestAttributeHandler eagerCsrfTokens() {
        XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(AppProperties props, PasswordEncoder encoder) {
        String password = props.admin().password();
        if (password == null || password.isBlank()) {
            password = UUID.randomUUID().toString();
            log.warn("ADMIN_PASSWORD is not set; generated admin password for this run: {}", password);
        }
        return new InMemoryUserDetailsManager(User.withUsername(props.admin().username())
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }
}
