package uz.murodjon.robotcallv2.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.port.input.AccessTokenUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.StreamTicketUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Which credential in which place turns a request into an identity — and which is ignored. */
class JwtAuthFilterTest {

    private static final AuthenticatedUser OPERATOR = new AuthenticatedUser(7L, 42L, 3L, "OPERATOR",
            Set.of(Permission.CAMPAIGN_READ), "Ali", "ali@example.com");

    private AccessTokenUseCase tokens;
    private StreamTicketUseCase streamTickets;
    private JwtAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        tokens = mock(AccessTokenUseCase.class);
        streamTickets = mock(StreamTicketUseCase.class);
        chain = mock(FilterChain.class);
        filter = new JwtAuthFilter(tokens, streamTickets);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ticketOnAStreamUrlAuthenticatesTheRequest() throws Exception {
        MockHttpServletRequest request = streamRequest("GET", "/api/live/stream");
        request.setParameter("ticket", "abc");
        when(streamTickets.consume("abc")).thenReturn(OPERATOR);

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(principal()).isEqualTo(OPERATOR);
        verify(chain).doFilter(any(), any());
    }

    /** The SSE ASYNC re-dispatch reuses the identity — the ticket itself is spent on the first pass. */
    @Test
    void asyncRedispatchReusesTheIdentityWithoutSpendingTheTicketAgain() throws Exception {
        MockHttpServletRequest request = streamRequest("GET", "/api/calls/PJSIP-1/listen");
        request.setParameter("ticket", "abc");
        when(streamTickets.consume("abc")).thenReturn(OPERATOR).thenReturn(null);

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        SecurityContextHolder.clearContext();
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(principal()).isEqualTo(OPERATOR);
        verify(streamTickets, times(1)).consume("abc");
    }

    @Test
    void spentOrUnknownTicketLeavesTheRequestAnonymous() throws Exception {
        MockHttpServletRequest request = streamRequest("GET", "/api/operators/me/events");
        request.setParameter("ticket", "spent");

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void ticketIsIgnoredOutsideTheStreamUrls() throws Exception {
        MockHttpServletRequest request = streamRequest("GET", "/api/campaigns");
        request.setParameter("ticket", "abc");

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verifyNoInteractions(streamTickets);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void ticketIsIgnoredOnAnythingButGet() throws Exception {
        MockHttpServletRequest request = streamRequest("POST", "/api/live/stream");
        request.setParameter("ticket", "abc");

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verifyNoInteractions(streamTickets);
    }

    @Test
    void deprecatedQueryTokenStillAuthenticatesAStream() throws Exception {
        MockHttpServletRequest request = streamRequest("GET", "/api/call-queues/live/stream");
        request.setParameter("token", "jwt");
        when(tokens.parse("jwt")).thenReturn(OPERATOR);

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(principal()).isEqualTo(OPERATOR);
    }

    @Test
    void bearerHeaderWinsAndLeavesTheTicketUnspent() throws Exception {
        MockHttpServletRequest request = streamRequest("GET", "/api/live/stream");
        request.addHeader("Authorization", "Bearer jwt");
        request.setParameter("ticket", "abc");
        when(tokens.parse("jwt")).thenReturn(OPERATOR);

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(principal()).isEqualTo(OPERATOR);
        verify(streamTickets, never()).consume(any());
    }

    private static MockHttpServletRequest streamRequest(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private static Object principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }
}
