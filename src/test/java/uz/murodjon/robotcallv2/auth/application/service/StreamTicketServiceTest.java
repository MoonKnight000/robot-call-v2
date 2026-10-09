package uz.murodjon.robotcallv2.auth.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.dto.StreamTicketResponse;
import uz.murodjon.robotcallv2.auth.application.port.output.StreamTicketStorePort;
import uz.murodjon.robotcallv2.auth.infrastructure.config.JwtProperties;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.util.Tokens;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreamTicketServiceTest {

    private static final String SECRET = "super-secure-secret-key-that-is-at-least-256-bits-long-12345678";
    private static final AuthenticatedUser OPERATOR = new AuthenticatedUser(7L, 42L, 3L, "OPERATOR",
            Set.of(Permission.CAMPAIGN_READ), "Ali", "ali@example.com");

    private TokenRevocationService revocation;
    private InMemoryStreamTicketStore store;
    private StreamTicketService service;

    @BeforeEach
    void setUp() {
        revocation = mock(TokenRevocationService.class);
        store = new InMemoryStreamTicketStore();
        service = new StreamTicketService(store, new JwtTokenService(new JwtProperties(SECRET, 60), revocation));
    }

    @Test
    void ticketAuthenticatesAsTheSameCallerExactlyOnce() {
        StreamTicketResponse issued = service.create(OPERATOR);

        AuthenticatedUser first = service.consume(issued.ticket());
        AuthenticatedUser second = service.consume(issued.ticket());

        assertThat(first).isNotNull();
        assertThat(first.userId()).isEqualTo(7L);
        assertThat(first.companyId()).isEqualTo(42L);
        assertThat(first.permissions()).containsExactly(Permission.CAMPAIGN_READ);
        assertThat(second).isNull();
    }

    @Test
    void ticketLivesSixtySecondsAndIsStoredOnlyByItsHash() {
        StreamTicketResponse issued = service.create(OPERATOR);

        assertThat(issued.expiresAt()).isBetween(Instant.now().plusSeconds(55), Instant.now().plusSeconds(61));
        assertThat(store.ttl).isEqualTo(Duration.ofSeconds(60));
        assertThat(store.values).containsOnlyKeys(Tokens.hash(issued.ticket()));
    }

    @Test
    void unknownTicketIsRefused() {
        assertThat(service.consume("never-issued")).isNull();
    }

    /** A logout, block or role change after the ticket was issued makes it worthless too. */
    @Test
    void ticketOfAUserRevokedSinceIsRefused() {
        StreamTicketResponse issued = service.create(OPERATOR);
        when(revocation.isRevoked(eq(7L), any())).thenReturn(true);

        assertThat(service.consume(issued.ticket())).isNull();
    }

    @Test
    void noTicketIsHandedOutWhenTheStoreIsDown() {
        StreamTicketStorePort down = mock(StreamTicketStorePort.class);
        StreamTicketService withoutStore = new StreamTicketService(down,
                new JwtTokenService(new JwtProperties(SECRET, 60), revocation));

        assertThatThrownBy(() -> withoutStore.create(OPERATOR)).isInstanceOf(ExternalServiceException.class);
    }

    /** GETDEL semantics: a ticket read once is gone. */
    private static final class InMemoryStreamTicketStore implements StreamTicketStorePort {

        private final Map<String, String> values = new HashMap<>();
        private Duration ttl;

        @Override
        public boolean save(String ticketHash, String accessToken, Duration ttl) {
            values.put(ticketHash, accessToken);
            this.ttl = ttl;
            return true;
        }

        @Override
        public String consume(String ticketHash) {
            return values.remove(ticketHash);
        }
    }
}
