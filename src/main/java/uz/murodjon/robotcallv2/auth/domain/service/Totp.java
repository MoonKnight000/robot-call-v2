package uz.murodjon.robotcallv2.auth.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * Time-based one-time passwords as every authenticator app computes them (RFC 6238:
 * HMAC-SHA1, 30-second steps, 6 digits), with the Base32 the apps exchange secrets in.
 * Pure — no Spring, no clock of its own — so it is tested against the RFC's own vectors.
 */
public final class Totp {

    private static final int SECRET_BYTES = 20;
    private static final long STEP_SECONDS = 30;
    /** A code one step either side of now is accepted: a phone's clock drifts. */
    private static final int WINDOW = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** A new secret, Base32 — what the user types in or scans. */
    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    /**
     * The time step {@code code} is valid for at {@code now}, or -1. Only steps after
     * {@code lastUsedStep} count, so a code already used cannot be used again.
     */
    public static long matchingStep(String secret, String code, Instant now, long lastUsedStep) {
        if (code == null || !code.matches("[0-9]{6}")) {
            return -1;
        }
        byte[] key = decodeBase32(secret);
        long current = now.getEpochSecond() / STEP_SECONDS;
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (step > lastUsedStep && MessageDigest.isEqual(
                    code(key, step).getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) {
                return step;
            }
        }
        return -1;
    }

    /** The {@code otpauth://} link an authenticator app reads from a QR code. */
    public static String otpauthUri(String issuer, String account, String secret) {
        String label = encode(issuer + ":" + account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=6&period=" + STEP_SECONDS;
    }

    /** The 6-digit code for {@code step} (RFC 4226 dynamic truncation). */
    static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is part of every JVM", e);
        }
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] decodeBase32(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                // Secrets are only ever written by base32() above; a bad one is a corrupted row.
                throw new ConflictException(ErrorCode.TOTP_SECRET_CORRUPT);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
