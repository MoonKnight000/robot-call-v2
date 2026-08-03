package uz.murodjon.uysotvoice.auth.dto;

import java.time.Instant;

/** Result of {@code JwtTokenService.issue} — the compact JWT plus its own expiry claim. */
public record IssuedToken(String token, Instant expiresAt) {
}
