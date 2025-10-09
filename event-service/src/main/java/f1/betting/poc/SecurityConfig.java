package f1.betting.poc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable()) // Disable CSRF for APIs
				.authorizeHttpRequests(auth -> auth
						// Public endpoint
						.requestMatchers("/api/events").permitAll()

						// Local-only endpoint
						.requestMatchers("/api/events/*/winner").access(localhostOnly())

						// Default: deny all others
						.anyRequest().denyAll()
				)
				.httpBasic(Customizer.withDefaults()); // optional, for testing local access

		return http.build();
	}

	private AuthorizationManager<RequestAuthorizationContext> localhostOnly() {
		return (Supplier<Authentication> authentication, RequestAuthorizationContext context) -> {
			String remoteAddr = context.getRequest().getRemoteAddr();
			// Allow only 127.0.0.1 (IPv4) or ::1 (IPv6)
			boolean allowed = "127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr);
			return new AuthorizationDecision(allowed);
		};
	}
}
