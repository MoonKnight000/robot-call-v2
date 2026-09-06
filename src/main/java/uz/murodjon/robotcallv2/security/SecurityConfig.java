package uz.murodjon.robotcallv2.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import uz.murodjon.robotcallv2.auth.application.service.JwtTokenService;
import uz.murodjon.robotcallv2.auth.infrastructure.config.JwtProperties;

import java.util.List;

/**
 * Locks down the HTTP surface (PROJECT.md §11). Everything under {@code /api/**} can
 * originate calls on the real SIP trunk or start a campaign, and {@code /actuator/**}
 * exposes infrastructure state — none of it may be reachable anonymously.
 *
 * <p>A request is authenticated one way only: a per-user {@code Authorization: Bearer} JWT
 * ({@link JwtAuthFilter}, ROADMAP E.1). Sessions and CSRF stay off — the token is stateless.
 * Kubernetes-style liveness/readiness probes stay open so an orchestrator can reach them
 * without a secret.
 *
 * <p><b>This class answers only "who is calling".</b> What that identity may do is decided
 * one endpoint at a time by {@code @PreAuthorize("hasAuthority('<PERMISSION>')")} on each
 * controller interface, enabled by {@link EnableMethodSecurity} — so a new endpoint is
 * guarded where it is declared, instead of by a path pattern somebody has to remember to
 * add here.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final SecurityProperties securityProperties;
    private final JwtProperties jwtProperties;
    private final JwtTokenService jwtTokenService;

    public SecurityConfig(SecurityProperties securityProperties, JwtProperties jwtProperties, JwtTokenService jwtTokenService) {
        this.securityProperties = securityProperties;
        this.jwtProperties = jwtProperties;
        this.jwtTokenService = jwtTokenService;
    }

    @PostConstruct
    public void warnIfUnconfigured() {
        if (!jwtProperties.configured()) {
            log.error("voice-agent.security.jwt.secret (env JWT_SECRET) is not set — nobody "
                    + "can log in and every /api/** request will be rejected with 401.");
        }
    }

    /** Used to hash {@code app_user.password_hash} (ROADMAP E.1) — never store plaintext. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
                // The filter never rejects on its own, it only populates the context (see its
                // javadoc); an unauthenticated request is stopped by authorizeHttpRequests below.
                .addFilterBefore(new JwtAuthFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    // Preflight carries no Authorization header by design (the browser sends it
                    // without credentials); it must clear the filter chain before the real request.
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // Probes carry no data and must work without a secret.
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    // Logging in, activating an invite, refreshing an expired access token, or
                    // recovering a forgotten password all happen without a currently-valid
                    // Bearer token.
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/activate",
                            "/api/auth/refresh", "/api/auth/uysot/callback", "/api/auth/forgot-password",
                            "/api/auth/reset-password").permitAll();
                    // The Uysot CRM OAuth redirect (§11 integrations) lands here straight from
                    // the Uysot server, carrying no Bearer token — the signed `state`
                    // param authenticates it instead (CrmIntegrationService
                    // #verifyState).
                    auth.requestMatchers(HttpMethod.GET, "/api/settings/integrations/uysot/callback").permitAll();
                    // Everything else needs an identity; which permission that identity has to
                    // hold is declared on the endpoint itself (@PreAuthorize). Routes with no
                    // annotation — /api/auth/me, /api/profile/**, /api/notifications/**,
                    // /api/files/**, /api/search — are open to any logged-in user on purpose:
                    // they act on the row of the caller or on data the company already shares.
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }

    /**
     * Permissive CORS configuration supporting localhost frontend dev servers and configured domains.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = securityProperties.allowedOrigins();
        if (origins == null || origins.isEmpty()) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOriginPatterns(origins);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }
}
