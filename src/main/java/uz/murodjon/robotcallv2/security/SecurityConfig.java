package uz.murodjon.robotcallv2.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.apikey.application.port.input.ApiKeyUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.AccessTokenUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.StreamTicketUseCase;
import uz.murodjon.robotcallv2.auth.infrastructure.config.JwtProperties;
import uz.murodjon.robotcallv2.shared.ratelimit.RateLimiter;

import java.util.List;

/**
 * Locks down the HTTP surface (PROJECT.md §11). Everything under {@code /api/**} can
 * originate calls on the real SIP trunk or start a campaign, and {@code /actuator/**}
 * exposes infrastructure state — none of it may be reachable anonymously.
 *
 * <p>A request is authenticated two ways: a per-user {@code Authorization: Bearer} JWT
 * ({@link JwtAuthFilter}, ROADMAP E.1), or a company's {@code X-Api-Key} for its own
 * systems ({@link ApiKeyAuthFilter}) — a caller with the same shape and fewer permissions,
 * so nothing downstream has to know which one it was. The JWT wins when both are present.
 * Sessions and CSRF stay off — both credentials are stateless. Kubernetes-style
 * liveness/readiness probes stay open so an orchestrator can reach them without a secret.
 *
 * <p><b>This class answers only "who is calling".</b> What that identity may do is decided
 * one endpoint at a time by {@code @PreAuthorize("hasAuthority('<PERMISSION>')")} on each
 * controller interface, enabled by {@link EnableMethodSecurity} — so a new endpoint is
 * guarded where it is declared, instead of by a path pattern somebody has to remember to
 * add here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final SecurityProperties securityProperties;
    private final JwtProperties jwtProperties;
    private final AccessTokenUseCase accessTokenUseCase;

    public SecurityConfig(SecurityProperties securityProperties, JwtProperties jwtProperties,
                          AccessTokenUseCase accessTokenUseCase) {
        this.securityProperties = securityProperties;
        this.jwtProperties = jwtProperties;
        this.accessTokenUseCase = accessTokenUseCase;
    }

    @PostConstruct
    public void warnIfUnconfigured() {
        if (!jwtProperties.configured()) {
            log.error("voice-agent.security.jwt.secret (env JWT_SECRET) is blank, shorter than 32 characters "
                    + "or still a CHANGE_ME placeholder — nobody can log in and every /api/** request "
                    + "will be rejected with 401.");
        }
    }

    /**
     * Standard Spring Security AuthenticationManager bean.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Used to hash {@code app_user.password_hash} (ROADMAP E.1) — never store plaintext.
     * Cost 12: ~250 ms per check on current hardware, which a login never notices and an
     * offline crack of a leaked table feels for every guess.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * The services are method parameters rather than constructor dependencies on purpose:
     * this class also defines the {@link PasswordEncoder} half the application needs, and
     * asking for a service in the constructor makes that encoder unavailable until the
     * whole service graph is built.
     */
    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, ApiKeyUseCase apiKeyUseCase, RateLimiter rateLimiter,
                                           RateLimitProperties rateLimitProperties,
                                           StreamTicketUseCase streamTicketUseCase,
                                           CallRouteRegistry callRouteRegistry) throws Exception {
        http
                // Stateless header auth: no session to fix, no form to forge.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // 401 instead of a redirect to a login page that does not exist.
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(headers -> headers
                        // The API serves JSON, audio and files, never a page: nothing under
                        // /api may load a script, a frame or a sub-resource. Scoped to /api so
                        // the static test panel, the widget script and the springdoc UI still
                        // render.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                                new StaticHeadersWriter("Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'")))
                        // Always, not only on a request Spring can see was TLS: behind a
                        // reverse proxy every request arrives as plain HTTP.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)
                                .requestMatcher(AnyRequestMatcher.INSTANCE))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                // Before any credential is looked at: a flood on login or the public widget
                // is answered 429 without touching the password hash or the database.
                .addFilterBefore(new RateLimitFilter(rateLimiter, rateLimitProperties),
                        UsernamePasswordAuthenticationFilter.class)
                // The filter never rejects on its own, it only populates the context (see its
                // javadoc); an unauthenticated request is stopped by authorizeHttpRequests below.
                .addFilterAfter(new JwtAuthFilter(accessTokenUseCase, streamTicketUseCase), RateLimitFilter.class)
                // After the JWT filter, and it steps aside when that one has already
                // decided: a request carrying both a Bearer token and a key is the person,
                // not the key.
                .addFilterAfter(new ApiKeyAuthFilter(apiKeyUseCase), JwtAuthFilter.class)
                // Once the caller is known, so only their company's calls are looked up; the
                // instance holding the call authorizes the replayed request itself.
                .addFilterAfter(new CallOwnerProxyFilter(callRouteRegistry, streamTicketUseCase),
                        ApiKeyAuthFilter.class)
                .authorizeHttpRequests(auth -> {
                    // Preflight carries no Authorization header by design (the browser sends it
                    // without credentials); it must clear the filter chain before the real request.
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // Probes carry no data and must work without a secret.
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    // Swagger UI and OpenAPI documentation
                    auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs", "/openapi/**").permitAll();
                    // Metrics describe the whole platform — every tenant's calls, the providers,
                    // the tool tags — so they are for platform staff. Any logged-in user or
                    // tenant API key used to be able to read them (an API key can never hold
                    // PLATFORM_ADMIN: ApiKeyScopes).
                    auth.requestMatchers("/actuator/**").hasAuthority("PLATFORM_ADMIN");
                    // Logging in, activating an invite, refreshing an expired access token, or
                    // recovering a forgotten password all happen without a currently-valid
                    // Bearer token.
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/activate",
                            "/api/auth/refresh", "/api/auth/uysot/callback", "/api/auth/forgot-password",
                            "/api/auth/reset-password").permitAll();
                    // The Uysot CRM OAuth redirect (§11 integrations) lands here straight from
                    // the Uysot server, carrying no Bearer token — the one-time `state`
                    // param authenticates it instead (CrmIntegrationService
                    // #consumeState).
                    auth.requestMatchers(HttpMethod.GET, "/api/settings/integrations/uysot/callback").permitAll();
                    // Inbound Uysot CRM webhooks (e.g. LEAD_CREATED speed-to-lead)
                    auth.requestMatchers(HttpMethod.POST, "/api/public/uysot/webhook").permitAll();
                    // The embed script on a customer's own website reads its widget's
                    // configuration and books its calls before anyone has logged into
                    // anything. The widget key is the only credential it has, and the
                    // allowed-origins list on the widget row is what stops that key being
                    // useful from another site; the agent's own daily and concurrent limits
                    // cap what a leaked key can spend (AgentWidgetService, WidgetCallGate).
                    auth.requestMatchers(HttpMethod.GET, "/api/public/widgets/*/config").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/public/widgets/*/session").permitAll();
                    // The same widget's text channel (UNIVERSAL-OPERATOR-PLAN E.4). Open for the
                    // same reason and capped the same way: the origin list, and the agent's
                    // chats of the day counted in Redis (ChatService, RedisChatQuotaAdapter).
                    auth.requestMatchers(HttpMethod.POST, "/api/public/widgets/*/chat",
                            "/api/public/widgets/*/chat/*").permitAll();
                    // Telegram posts a bot's updates here. It has no account on this platform,
                    // so what makes an update genuine is the secret it carries in
                    // X-Telegram-Bot-Api-Secret-Token — checked in TelegramService, in constant
                    // time, against the one this bot was registered with.
                    auth.requestMatchers(HttpMethod.POST, "/api/public/telegram/*").permitAll();
                    // The embed script itself, served from static resources — it is what a
                    // visitor's browser fetches first, before it knows anything at all.
                    auth.requestMatchers(HttpMethod.GET, "/widgets/**").permitAll();
                    // Payment providers' servers post here with no Bearer token. Each call is
                    // authenticated by the billing use case instead: Payme by HTTP Basic
                    // Paycom:<key> (PaymeMerchantService), Click by the md5 sign_string over
                    // its secret key (ClickMerchantService).
                    auth.requestMatchers(HttpMethod.POST, "/api/billing/payme/callback",
                            "/api/billing/click/prepare", "/api/billing/click/complete").permitAll();
                    // Everything else needs an identity; which permission that identity has to
                    // hold is declared on the endpoint itself (@PreAuthorize). Routes with no
                    // annotation — /api/auth/me, /api/profile/**, /api/notifications/**,
                    // /api/files/** — are open to any logged-in user on purpose:
                    // they act on the row of the caller or on data the company already shares.
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }

    /**
     * Request headers the console may send cross-origin: what the backend reads, plus what
     * the console's fetch-based SSE client adds ({@code Last-Event-ID} on a reconnect).
     */
    private static final List<String> CONSOLE_REQUEST_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Api-Key", HttpHeaders.RANGE,
            HttpHeaders.ACCEPT, HttpHeaders.ACCEPT_LANGUAGE, "Last-Event-ID");

    /** Response headers the console's script may read: downloads, ranged audio and 429 back-off. */
    private static final List<String> CONSOLE_EXPOSED_HEADERS = List.of(
            HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.CONTENT_RANGE, HttpHeaders.ACCEPT_RANGES,
            HttpHeaders.CONTENT_LENGTH, HttpHeaders.RETRY_AFTER);

    /**
     * CORS for the console: the configured origins, with credentials (the refresh token is
     * an HttpOnly cookie). An empty list means no cross-origin caller at all — never
     * "everyone": a wildcard with credentials would let any page a logged-in operator visits
     * call this API as them. The header lists are explicit for the same reason — a new
     * header crossing origins is a decision, not a default.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = securityProperties.allowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOriginPatterns(origins);
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
            configuration.setAllowedHeaders(CONSOLE_REQUEST_HEADERS);
            configuration.setExposedHeaders(CONSOLE_EXPOSED_HEADERS);
            configuration.setAllowCredentials(true);
            source.registerCorsConfiguration("/**", configuration);
        } else {
            log.warn("voice-agent.security.allowed-origins (CORS_ALLOWED_ORIGINS) is empty — no browser "
                    + "origin other than this app's own may call the API");
        }

        // The embed script runs on the customer's own website, which is by definition not
        // one of our configured origins — locking `allowed-origins` down for the console
        // would otherwise silently break every widget in the field. These two paths are
        // therefore open to any origin, and it costs nothing: they carry no cookie and no
        // Authorization header (credentials are off, and the script sends none), so a
        // hostile page reading them learns only what it could have read with curl. What
        // actually decides whether a call may start is the widget's own allowed-origins
        // list, checked server-side against the Origin header in AgentWidgetService.
        CorsConfiguration publicWidget = new CorsConfiguration();
        publicWidget.setAllowedOriginPatterns(List.of("*"));
        publicWidget.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        publicWidget.setAllowedHeaders(List.of("*"));
        publicWidget.setAllowCredentials(false);
        source.registerCorsConfiguration("/api/public/widgets/**", publicWidget);
        source.registerCorsConfiguration("/widgets/**", publicWidget);
        return source;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }
}
