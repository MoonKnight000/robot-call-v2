package uz.murodjon.robotcallv2.auth.application.dto;

import java.time.Instant;

/**
 * @param ticket    single-use credential for {@code ?ticket=} on a stream URL
 * @param expiresAt after this it is refused even if never used
 */
public record StreamTicketResponse(
        String ticket,
        Instant expiresAt
) {
}
