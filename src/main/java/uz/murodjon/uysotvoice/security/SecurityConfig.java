package uz.murodjon.uysotvoice.security;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import uz.murodjon.uysotvoice.auth.config.JwtProperties;
import uz.murodjon.uysotvoice.auth.service.JwtTokenService;

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
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Role names without the {@code ROLE_} prefix, which {@code hasRole} adds itself. */
    private static final String ADMIN = "ADMIN";
    private static final String OPERATOR = "OPERATOR";
    private static final String VIEWER = "VIEWER";
    private static final String SUPERADMIN = "SUPERADMIN";

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
                // each filter's javadoc), so order between them does not matter — whichever
                // header is present wins, and a request with both is not expected to occur.
                .addFilterBefore(new ApiKeyFilter(props.apiKey(), props.readApiKey()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
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
                    // Logging in, activating an invite, refreshing an expired access token, or
                    // recovering a forgotten password all happen without a currently-valid
                    // Bearer token.
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/activate",
                            "/api/auth/refresh", "/api/auth/uysot/callback", "/api/auth/forgot-password",
                            "/api/auth/reset-password").permitAll();
                    // The Uysot CRM OAuth redirect (§11 integrations) lands here straight from
                    // Uysot's own server, carrying neither X-Api-Key nor a Bearer token — the
                    // signed `state` param authenticates it instead (CrmIntegrationService
                    // #verifyState). Must be checked before the /api/settings/** ADMIN bucket
                    // below, which would otherwise shadow it.
                    auth.requestMatchers(HttpMethod.GET, "/api/settings/integrations/uysot/callback").permitAll();
                    // Every logged-in role needs its own company (sidebar switcher, UI-DESIGN
                    // §7.3) — this exact GET must be checked before the broader
                    // /api/companies/** admin bucket below, or that would shadow it.
                    auth.requestMatchers(HttpMethod.GET, "/api/companies").authenticated();
                    // Tenant onboarding (creating a company), listing every tenant, and
                    // changing a company's status are platform-staff-only (SUPERADMIN, report
                    // #3) — a tenant's own ADMIN must not be able to suspend/reactivate itself
                    // or see other tenants. Checked before the general /api/companies/** ADMIN
                    // bucket below, which would otherwise shadow these with a broader role.
                    // GET /api/companies/{id} stays reachable by both: a tenant ADMIN views its
                    // own company (self-scoped in CompanyService), a superadmin views any of
                    // them while deciding on a status change.
                    auth.requestMatchers(HttpMethod.POST, "/api/companies").hasRole(SUPERADMIN);
                    auth.requestMatchers(HttpMethod.POST, "/api/companies/list").hasRole(SUPERADMIN);
                    auth.requestMatchers(HttpMethod.PUT, "/api/companies/*/status").hasRole(SUPERADMIN);
                    auth.requestMatchers(HttpMethod.GET, "/api/companies/*").hasAnyRole(SUPERADMIN, ADMIN);
                    // Any logged-in identity (any role) — these only ever act on the caller's
                    // own session, never on another company's data. /api/profile/** (§15) is
                    // the self-service profile page — same reasoning, every method resolves
                    // the target row via CurrentUser, not a path id.
                    auth.requestMatchers("/api/auth/me", "/api/auth/logout", "/api/search",
                            "/api/notifications/**", "/api/profile/**").authenticated();
                    // Admin-only (ROADMAP E.1 / UI-DESIGN §8.2 "faqat admin uchun"): user
                    // management, and tenant-level settings (company identity/config, SIP
                    // trunks) — checked before the general operator bar below so neither is
                    // shadowed by anyRequest().
                    auth.requestMatchers("/api/users/**", "/api/companies/**", "/api/sip-trunks/**",
                                    "/api/settings/**")
                            .hasRole(ADMIN);
                    // Reading results is separable from acting: reporting and metrics are open
                    // to the read-only key, everything else needs the key that can dial.
                    auth.requestMatchers(HttpMethod.GET, "/api/reports/**")
                            .hasRole(VIEWER);
                    // Same read-only bar as reporting: this is a push feed of state
                    // reporting already exposes, not an endpoint that changes anything.
                    auth.requestMatchers(HttpMethod.GET, "/api/live/**")
                            .hasRole(VIEWER);
                    // Any logged-in identity (any role) — a company logo or a teammate's
                    // avatar is visible to everyone in the company regardless of role;
                    // FileStorageService#download enforces the own-company/SUPERADMIN
                    // boundary itself, same as every other cross-tenant lookup.
                    auth.requestMatchers(HttpMethod.GET, "/api/files/**").authenticated();
                    auth.requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole(VIEWER);
                    // Every remaining route either changes state or places a call — day-to-day
                    // operational work (campaigns, calls, contacts, scenarios, inbound routes),
                    // open to OPERATOR and ADMIN alike (ROADMAP E.1 role tier). Note this covers
                    // GETs outside /api/reports too (e.g. listing campaigns), which is the safe
                    // default for a route added later.
                    auth.anyRequest().hasRole(OPERATOR);
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
        configuration.setAllowedHeaders(List.of(ApiKeyFilter.HEADER, "Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
