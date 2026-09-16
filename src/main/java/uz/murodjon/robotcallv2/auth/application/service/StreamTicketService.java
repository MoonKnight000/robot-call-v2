package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.dto.StreamTicketResponse;
import uz.murodjon.robotcallv2.auth.application.port.input.StreamTicketUseCase;
import uz.murodjon.robotcallv2.auth.application.port.output.StreamTicketStorePort;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.util.Tokens;

import java.time.Duration;
import java.time.Instant;

/**
 * Single-use tickets for the stream URLs a browser's {@code EventSource}/{@code <audio>}
 * cannot put an {@code Authorization} header on. The URL lands in access logs, proxy logs
 * and browser history; a ticket found there is already spent, or dead within a minute,
 * where the access token it replaces stayed usable for an hour.
 *
 * <p>A ticket stands for an access token signed like any other, so the filter that accepts
 * it re-checks signature, expiry and revocation on the way in, and write access to Redis
 * alone cannot forge one. Only the ticket's hash is used as the key.
 */
@Service
public class StreamTicketService implements StreamTicketUseCase {

    /** Long enough to open the stream right after asking for it, too short to be worth stealing. */
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final StreamTicketStorePort streamTicketStore;
    private final JwtTokenService jwtTokenService;

    public StreamTicketService(StreamTicketStorePort streamTicketStore, JwtTokenService jwtTokenService) {
        this.streamTicketStore = streamTicketStore;
        this.jwtTokenService = jwtTokenService;
    }

    public StreamTicketResponse issue(AuthenticatedUser principal) {
        String ticket = Tokens.generate();
        String accessToken = jwtTokenService.issue(principal).token();
        if (!streamTicketStore.save(Tokens.hash(ticket), accessToken, TICKET_TTL)) {
            throw new ExternalServiceException(ErrorCode.STREAM_TICKET_STORE_UNAVAILABLE, "redis");
        }
        return new StreamTicketResponse(ticket, Instant.now().plus(TICKET_TTL));
    }

    @Override
    public AuthenticatedUser consume(String ticket) {
        String accessToken = streamTicketStore.consume(Tokens.hash(ticket));
        return accessToken == null ? null : jwtTokenService.parse(accessToken);
    }
}
