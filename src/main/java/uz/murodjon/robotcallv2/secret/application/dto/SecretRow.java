package uz.murodjon.robotcallv2.secret.application.dto;

import java.time.Instant;

public record SecretRow(
        long id,
        long companyId,
        String key,
        String maskedValue,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Enough to tell two secrets apart, never enough to guess one: at most the last two
     * characters, and never more than a quarter of the value. The old first-three-and-
     * last-three showed six characters of an eight-character secret.
     */
    public static String mask(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "******";
        }
        int shown = Math.min(2, plaintext.length() / 4);
        return shown == 0 ? "******" : "******" + plaintext.substring(plaintext.length() - shown);
    }
}
