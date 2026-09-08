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
    public static String mask(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "******";
        }
        int len = plaintext.length();
        if (len <= 6) {
            return "******";
        }
        return plaintext.substring(0, 3) + "..." + plaintext.substring(len - 3);
    }
}
