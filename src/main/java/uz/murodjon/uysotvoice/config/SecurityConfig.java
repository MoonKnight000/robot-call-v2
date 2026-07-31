package uz.murodjon.uysotvoice.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Locks down the HTTP surface (PROJECT.md §11). Everything under {@code /api/**} can
 * originate calls on the real SIP trunk or start a campaign, and {@code /actuator/**}
 * exposes infrastructure state — none of it may be reachable anonymously.
 *
 * <p>Authentication is a shared {@code X-Api-Key} header ({@link ApiKeyFilter}); this
 * is a machine-to-machine API with no user accounts, so sessions and CSRF are off.
 * Kubernetes-style liveness/readiness probes stay open so an orchestrator can reach
 * them without a secret.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Role names without the {@code ROLE_} prefix, which {@code hasRole} adds itself. */
    private static final String ADMIN = "ADMIN";
    private static final String VIEWER = "VIEWER";

    private final SecurityProperties props;

    public SecurityConfig(SecurityProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void warnIfUnconfigured() {
        if (!props.configured()) {
            log.error("voice-agent.security.api-key (env API_KEY) is not set — every /api/** "
                    + "request will be rejected with 401. Set it before making calls.");
        }
        if (props.readOnlyConfigured() && props.apiKey() != null
                && props.apiKey().equals(props.readApiKey())) {
            // Same value for both means the "read-only" key can dial subscribers, which is
            // the opposite of what configuring it was for.
            log.error("voice-agent.security.read-api-key is identical to api-key — the "
                    + "read-only key grants full admin access. Use a different secret.");
        }
    }

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                // Stateless header auth: no session to fix, no form to forge.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // 401 instead of a redirect to a login page that does not exist.
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new ApiKeyFilter(props.apiKey(), props.readApiKey()),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    // Preflight carries no X-Api-Key by design (the browser sends it without
                    // credentials); it must clear the filter chain before the real request.
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // Probes carry no data and must work without a secret.
                    auth.requestMatchers(EndpointRequest.to("health")).permitAll();
                    if (props.publicUi()) {
                        // The panel is a static page; the API calls it makes still need the key.
                        auth.requestMatchers("/", "/index.html", "/favicon.ico",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }
                    // Reading results is separable from acting: reporting and metrics are open
                    // to the read-only key, everything else needs the key that can dial.
                    auth.requestMatchers(HttpMethod.GET, "/api/reports/**")
                            .hasRole(VIEWER);
                    // Same read-only bar as reporting: this is a push feed of state
                    // reporting already exposes, not an endpoint that changes anything.
                    auth.requestMatchers(HttpMethod.GET, "/api/live/**")
                            .hasRole(VIEWER);
                    auth.requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole(VIEWER);
                    // Every remaining route either changes state or places a call. Note this
                    // covers GETs outside /api/reports too (e.g. listing campaigns), which is
                    // the safe default for a route added later.
                    auth.anyRequest().hasRole(ADMIN);
                });
        return http.build();
    }

    /**
     * CORS applies to browsers only, not to server-to-server or curl calls, so an empty
     * origin list (the default) simply means no browser origin other than this app's own
     * is allowed — the static test panel keeps working either way.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(props.allowedOrigins() == null ? List.of() : props.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(ApiKeyFilter.HEADER, "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
