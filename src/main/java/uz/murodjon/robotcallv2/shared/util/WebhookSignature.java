package uz.murodjon.robotcallv2.shared.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * How this application signs what it POSTs to a company's endpoint, in one place — the
 * post-call webhooks, the notification webhook channel and the call-flow router all sign
 * the same way, so a receiver writes one verifier.
 *
 * <p>{@code X-RobotCall-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256 of "t.body">}. The
 * timestamp is inside the signed string on purpose: without it a receiver can verify that
 * we sent the body but not when, and a delivery replayed a month later verifies as well as
 * a fresh one.
 */
public final class WebhookSignature {

    public static final String SIGNATURE_HEADER = "X-RobotCall-Signature";

    /** An id for one delivery, the same on each of its retries, so a receiver can drop a duplicate. */
    public static final String DELIVERY_HEADER = "X-RobotCall-Delivery";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    /** The {@link #SIGNATURE_HEADER} value for {@code body}, signed at {@code epochSeconds}. */
    public static String createHeaderValue(String body, String secret, long epochSeconds) {
        return "t=" + epochSeconds + ",v1=" + computeHmacSha256Hex(secret, epochSeconds + "." + body);
    }

    /** Hex HMAC-SHA256 of {@code message} under {@code secret}, both read as UTF-8. */
    public static String computeHmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            // Every JVM ships HmacSHA256; reaching here means the platform is broken, not
            // that a webhook is misconfigured.
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
