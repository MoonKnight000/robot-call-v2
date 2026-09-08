package uz.murodjon.robotcallv2.apikey.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * How an API key is made and recognised.
 *
 * <p>Shape: {@code rc_live_<prefix>_<secret>}. The prefix is stored and shown so a key can
 * be told apart in a list and found in one indexed lookup; the secret is not stored at all,
 * only the SHA-256 of the whole string.
 *
 * <p>SHA-256 rather than bcrypt on purpose. A password is short and guessable and has to
 * be made expensive to try; this secret is 256 bits of {@link SecureRandom} and cannot be
 * guessed, while the check runs on every API request and must not cost 100ms.
 */
public final class ApiKeySecret {

    /** {@code rc} for this platform, {@code live} to leave room for a test key later. */
    private static final String PREFIX_NAMESPACE = "rc_live_";
    private static final int PREFIX_BYTES = 6;
    private static final int SECRET_BYTES = 32;

    /**
     * The prefix is hex, not base64url, so that it is a fixed length and holds no
     * underscore of its own. That is what lets {@link #readPrefix} cut a presented key at
     * a known position instead of searching for a separator the random half could contain.
     */
    private static final int PREFIX_LENGTH = PREFIX_NAMESPACE.length() + PREFIX_BYTES * 2;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private ApiKeySecret() {
    }

    /** The readable half, unique per key: {@code rc_live_<12 hex>}. */
    public static String generatePrefix() {
        return PREFIX_NAMESPACE + HexFormat.of().formatHex(randomBytes(PREFIX_BYTES));
    }

    /** The whole credential, returned to its owner once and never stored. */
    public static String generateKey(String prefix) {
        return prefix + "_" + ENCODER.encodeToString(randomBytes(SECRET_BYTES));
    }

    /**
     * The prefix a presented key claims to be, or null when it is not shaped like one of
     * ours — checked before anything touches the database, so a malformed header costs a
     * substring rather than a query.
     */
    public static String readPrefix(String presentedKey) {
        if (presentedKey == null
                || presentedKey.length() <= PREFIX_LENGTH
                || !presentedKey.startsWith(PREFIX_NAMESPACE)
                || presentedKey.charAt(PREFIX_LENGTH) != '_') {
            return null;
        }
        return presentedKey.substring(0, PREFIX_LENGTH);
    }

    public static String hash(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256; reaching here means the platform is broken.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Constant-time comparison. A byte-by-byte {@code equals} leaks how much of a hash was
     * right through how long it took to say no, which is enough to reconstruct one.
     */
    public static boolean matches(String presentedKey, String storedHash) {
        if (presentedKey == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(presentedKey).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
