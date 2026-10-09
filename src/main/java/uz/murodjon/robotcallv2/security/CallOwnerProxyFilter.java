package uz.murodjon.robotcallv2.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.port.input.StreamTicketUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hands a request about a live call to the instance that holds it. Every instance holds
 * its own calls' media and state, and behind a load balancer the hang-up, transfer,
 * whisper or listen of a call lands on any of them — the owner is looked up in
 * {@link CallRouteRegistry}, the request is replayed there with the caller's own
 * credentials, and the answer is streamed back as it comes (a {@code listen} never ends).
 *
 * <p>After authentication, so only a company's own calls are looked up; before
 * authorization, which the owner applies to the replayed request itself. A replayed
 * request carries {@link #FORWARDED_HEADER} and is never forwarded again. When the owner
 * cannot be reached the request is answered {@code 409 CALL_ON_OTHER_INSTANCE}, as it was
 * before there was a proxy.
 */
public class CallOwnerProxyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CallOwnerProxyFilter.class);

    static final String FORWARDED_HEADER = "X-Forwarded-By-Instance";

    /** Every endpoint that acts on one live call, by its channel id. */
    private static final Pattern CALL_PATH = Pattern.compile("^/api/(?:"
            + "calls/(?:live/)?([^/]+)/(?:play|say|hangup|transfer|hold|unhold|consult|consult/complete"
            + "|consult/cancel|monitor|listen|whisper|takeover|scenario)"
            + "|operator/calls/([^/]+)(?:/takeover|/whisper)?)$");
    /** Hop-by-hop, or set by the client from the body: never copied across. */
    private static final Set<String> NOT_COPIED = Set.of("host", "content-length", "connection", "keep-alive",
            "transfer-encoding", "te", "trailer", "upgrade", "expect", "proxy-connection");
    private static final ObjectMapper ERROR_WRITER = new ObjectMapper();

    private final CallRouteRegistry callRouteRegistry;
    private final StreamTicketUseCase streamTicketUseCase;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public CallOwnerProxyFilter(CallRouteRegistry callRouteRegistry, StreamTicketUseCase streamTicketUseCase) {
        this.callRouteRegistry = callRouteRegistry;
        this.streamTicketUseCase = streamTicketUseCase;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String channelId = channelIdOf(request.getRequestURI());
        AuthenticatedUser user = channelId != null && request.getHeader(FORWARDED_HEADER) == null ? currentUser() : null;
        String owner = user != null ? callRouteRegistry.findInstanceOf(user.companyId(), channelId) : null;
        String ownerUrl = owner != null && !owner.equals(callRouteRegistry.instanceId())
                ? callRouteRegistry.findInternalUrl(owner) : null;
        if (ownerUrl == null) {
            // Not a call request, not authenticated, this instance's call, or an owner
            // nobody can reach: the controller answers it, 409 included.
            chain.doFilter(request, response);
            return;
        }
        try {
            forward(request, response, ownerUrl, user);
        } catch (IOException | InterruptedException | RuntimeException e) {
            // RuntimeException: no fresh stream ticket (its store is down) or a bad owner URL.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Request {} {} not forwarded to instance {}: {}", request.getMethod(),
                    request.getRequestURI(), owner, e.getMessage());
            if (!response.isCommitted()) {
                writeOnOtherInstance(response, channelId, owner);
            }
        }
    }

    private void forward(HttpServletRequest request, HttpServletResponse response, String ownerUrl,
                         AuthenticatedUser user) throws IOException, InterruptedException {
        String query = withFreshTicket(request.getQueryString(), user);
        URI target = URI.create(ownerUrl.replaceAll("/+$", "") + request.getRequestURI()
                + (query != null ? "?" + query : ""));
        byte[] body = request.getInputStream().readAllBytes();
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .method(request.getMethod(), body.length > 0
                        ? HttpRequest.BodyPublishers.ofByteArray(body)
                        : HttpRequest.BodyPublishers.noBody());
        for (String name : Collections.list(request.getHeaderNames())) {
            if (!NOT_COPIED.contains(name.toLowerCase(Locale.ROOT))) {
                for (String value : Collections.list(request.getHeaders(name))) {
                    builder.header(name, value);
                }
            }
        }
        builder.header(FORWARDED_HEADER, callRouteRegistry.instanceId());

        HttpResponse<InputStream> answer = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        response.setStatus(answer.statusCode());
        for (Map.Entry<String, List<String>> header : answer.headers().map().entrySet()) {
            if (!NOT_COPIED.contains(header.getKey().toLowerCase(Locale.ROOT)) && !header.getKey().startsWith(":")) {
                header.getValue().forEach(value -> response.addHeader(header.getKey(), value));
            }
        }
        try (InputStream in = answer.body(); OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                // A listen stream is audio in real time: each chunk goes out as it arrives.
                out.flush();
            }
        }
    }

    /**
     * The query with its {@code ticket} replaced by a new one for the same user: the
     * ticket the request came with is single-use, and {@code JwtAuthFilter} here already
     * spent it — replayed as it was, the owner would refuse it.
     */
    private String withFreshTicket(String query, AuthenticatedUser user) {
        if (query == null) {
            return null;
        }
        StringBuilder rewritten = new StringBuilder();
        for (String parameter : query.split("&")) {
            if (!rewritten.isEmpty()) {
                rewritten.append('&');
            }
            rewritten.append(parameter.startsWith("ticket=")
                    ? "ticket=" + URLEncoder.encode(streamTicketUseCase.create(user).ticket(), StandardCharsets.UTF_8)
                    : parameter);
        }
        return rewritten.toString();
    }

    private static String channelIdOf(String path) {
        Matcher matcher = CALL_PATH.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user : null;
    }

    private static void writeOnOtherInstance(HttpServletResponse response, String channelId, String owner)
            throws IOException {
        ResponseData<Void> body = ResponseData.error(ErrorCode.CALL_ON_OTHER_INSTANCE,
                ErrorCode.CALL_ON_OTHER_INSTANCE.format(channelId, owner));
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(ERROR_WRITER.writeValueAsString(body));
    }
}
