package f1.betting.poc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public swagger and OpenAPI endpoints
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Public APIs for this POC
                        .requestMatchers("/api/**").permitAll()
                        // Anything else also permitted (adjust as needed later)
                        .anyRequest().permitAll()
                )
                // optional basic to avoid redirect to login form; with permitAll it shouldn't trigger, but harmless
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
