package uz.murodjon.robotcallv2.security;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.dto.StreamTicketResponse;
import uz.murodjon.robotcallv2.auth.application.port.input.StreamTicketUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Which requests are handed to the instance holding the call, and that the owner gets the
 * caller's credentials — a usable ticket included — and the caller gets the owner's answer.
 */
class CallOwnerProxyFilterTest {

    private static final AuthenticatedUser OPERATOR = new AuthenticatedUser(7L, 42L, 3L, "OPERATOR",
            Set.of(Permission.CAMPAIGN_READ), "Ali", "ali@example.com");

    private CallRouteRegistry routes;
    private StreamTicketUseCase streamTickets;
    private FilterChain chain;
    private CallOwnerProxyFilter filter;
    private HttpServer owner;
    private final AtomicReference<String> ownerSawUri = new AtomicReference<>();
    private final AtomicReference<String> ownerSawAuthorization = new AtomicReference<>();
    private final AtomicReference<String> ownerSawForwardedBy = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        routes = mock(CallRouteRegistry.class);
        streamTickets = mock(StreamTicketUseCase.class);
        chain = mock(FilterChain.class);
        filter = new CallOwnerProxyFilter(routes, streamTickets);
        when(routes.instanceId()).thenReturn("app-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(OPERATOR, null, List.of()));

        owner = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        owner.createContext("/", exchange -> {
            ownerSawUri.set(exchange.getRequestURI().toString());
            ownerSawAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            ownerSawForwardedBy.set(exchange.getRequestHeaders().getFirst(CallOwnerProxyFilter.FORWARDED_HEADER));
            byte[] body = "{\"accept\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        owner.start();
    }

    @AfterEach
    void tearDown() {
        owner.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    void aCallOnAnotherInstanceIsAnsweredByThatInstance() throws Exception {
        givenCallOn("app-2", "http://127.0.0.1:" + owner.getAddress().getPort());
        MockHttpServletRequest request = request("POST", "/api/calls/PJSIP-1/hangup");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"accept\":true}");
        assertThat(ownerSawUri.get()).isEqualTo("/api/calls/PJSIP-1/hangup");
        assertThat(ownerSawAuthorization.get()).isEqualTo("Bearer token");
        assertThat(ownerSawForwardedBy.get()).isEqualTo("app-1");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void theSpentTicketIsReplacedWithAFreshOne() throws Exception {
        givenCallOn("app-2", "http://127.0.0.1:" + owner.getAddress().getPort());
        when(streamTickets.create(OPERATOR)).thenReturn(new StreamTicketResponse("fresh", Instant.now()));
        MockHttpServletRequest request = request("GET", "/api/calls/live/PJSIP-1/whisper");
        request.setQueryString("ticket=spent&x=1");

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(ownerSawUri.get()).isEqualTo("/api/calls/live/PJSIP-1/whisper?ticket=fresh&x=1");
    }

    @Test
    void aCallOfThisInstanceIsHandledHere() throws Exception {
        when(routes.findInstanceOf(42L, "PJSIP-1")).thenReturn("app-1");

        filter.doFilterInternal(request("POST", "/api/calls/PJSIP-1/hangup"), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertThat(ownerSawUri.get()).isNull();
    }

    @Test
    void aRequestAboutNoCallIsNotLookedUp() throws Exception {
        filter.doFilterInternal(request("GET", "/api/calls/live"), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(streamTickets);
        verify(routes, never()).findInstanceOf(anyLong(), any());
    }

    @Test
    void anAlreadyForwardedRequestIsNeverForwardedAgain() throws Exception {
        givenCallOn("app-2", "http://127.0.0.1:" + owner.getAddress().getPort());
        MockHttpServletRequest request = request("POST", "/api/operator/calls/PJSIP-1/takeover");
        request.addHeader(CallOwnerProxyFilter.FORWARDED_HEADER, "app-3");

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertThat(ownerSawUri.get()).isNull();
    }

    @Test
    void anUnreachableOwnerIsAnsweredWithItsName() throws Exception {
        givenCallOn("app-2", "http://127.0.0.1:1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request("POST", "/api/calls/PJSIP-1/hangup"), response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("CALL_ON_OTHER_INSTANCE").contains("app-2");
        verify(chain, never()).doFilter(any(), any());
    }

    private void givenCallOn(String instance, String url) {
        when(routes.findInstanceOf(42L, "PJSIP-1")).thenReturn(instance);
        when(routes.findInternalUrl(instance)).thenReturn(url);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
