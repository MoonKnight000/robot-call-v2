package uz.murodjon.robotcallv2.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The signature is a contract with every receiver's verifier, so its exact bytes are pinned here. */
class WebhookSignatureTest {

    @Test
    void hmacMatchesTheRfc4231TestVector() {
        // RFC 4231 test case 2.
        assertThat(WebhookSignature.computeHmacSha256Hex("Jefe", "what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void headerSignsTheTimestampAndTheBodyTogether() {
        String body = "{\"event\":\"CALL_ENDED\"}";

        String header = WebhookSignature.createHeaderValue(body, "secret", 1757203200L);

        assertThat(header).isEqualTo("t=1757203200,v1="
                + WebhookSignature.computeHmacSha256Hex("secret", "1757203200." + body));
    }
}
