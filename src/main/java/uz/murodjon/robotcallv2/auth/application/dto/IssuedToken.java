package uz.murodjon.robotcallv2.auth.application.dto;

import java.time.Instant;

public record IssuedToken(
        String token,
        Instant expiresAt
) {
}
