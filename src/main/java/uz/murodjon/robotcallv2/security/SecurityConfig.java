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
 * <p>Two independent authentications run in the same chain: a shared {@code X-Api-Key}
 * header ({@link ApiKeyFilter}) for machine-to-machine callers, and a per-user
 * {@code Authorization: Bearer} JWT ({@link JwtAuthFilter}, ROADMAP E.1) for the panel.
 * Either can authenticate a request; sessions and CSRF stay off regardless — both are
 * stateless. Kubernetes-style liveness/readiness probes stay open so an orchestrator can
 * reach them without a secret.
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

    private final SecurityProperties props;
    private final JwtProperties jwtProps;
    private final JwtTokenService jwtTokenService;

    public SecurityConfig(SecurityProperties props, JwtProperties jwtProps, JwtTokenService jwtTokenService) {
        this.props = props;
        this.jwtProps = jwtProps;
        this.jwtTokenService = jwtTokenService;
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
        if (!jwtProps.configured()) {
            log.error("voice-agent.security.jwt.secret is not set — no user can log in "
                    + "(ROADMAP E.1); X-Api-Key requests are unaffected.");
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
                // Neither filter ever rejects on its own, only populates the context (see
                // the javadoc on each), so order between them does not matter — whichever
                // header is present wins, and a request with both is not expected to occur.
                .addFilterBefore(new ApiKeyFilter(props.apiKey(), props.readApiKey()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    // Preflight carries no X-Api-Key by design (the browser sends it without
                    // credentials); it must clear the filter chain before the real request.
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
                    // the Uysot server, carrying neither X-Api-Key nor a Bearer token — the
                    // signed `state` param authenticates it instead (CrmIntegrationService
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
        List<String> origins = props.allowedOrigins();
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
