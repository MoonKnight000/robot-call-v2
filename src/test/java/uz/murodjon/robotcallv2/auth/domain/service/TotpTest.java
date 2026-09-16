package uz.murodjon.robotcallv2.auth.domain.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B: the SHA-1 seed, whose 8-digit code at T=59 s is 94287082. */
    private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfcTestVector() {
        assertThat(Totp.code(RFC_SEED, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_SEED, 1111111109L / 30)).isEqualTo("081804");
    }

    @Test
    void base32RoundTrips() {
        String secret = Totp.base32(RFC_SEED);
        assertThat(secret).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.decodeBase32(secret)).isEqualTo(RFC_SEED);
    }

    @Test
    void acceptsTheCurrentCodeOnceAndNeighbouringStepsForDrift() {
        String secret = Totp.base32(RFC_SEED);
        Instant now = Instant.ofEpochSecond(59);
        long step = Totp.matchingStep(secret, "287082", now, 0);

        assertThat(step).isEqualTo(1);
        // The same code again is a replay.
        assertThat(Totp.matchingStep(secret, "287082", now, step)).isEqualTo(-1);
        // A phone 30 s behind still gets in.
        assertThat(Totp.matchingStep(secret, "287082", now.plusSeconds(30), 0)).isEqualTo(1);
    }

    @Test
    void rejectsMalformedCodes() {
        String secret = Totp.base32(RFC_SEED);
        assertThat(Totp.matchingStep(secret, "28708", Instant.ofEpochSecond(59), 0)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, "abcdef", Instant.ofEpochSecond(59), 0)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, null, Instant.ofEpochSecond(59), 0)).isEqualTo(-1);
    }
}
