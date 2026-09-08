package uz.murodjon.robotcallv2.apikey.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Instant;
import java.util.Set;

/**
 * @param name      what this key is for, so it can be recognised and revoked later
 * @param scopes    permission names it may exercise; anything no key may hold is dropped
 * @param expiresAt when it should stop working, or null for a key with no end date
 */
public record CreateApiKeyRequest(
        @NotBlank(message = "Kalit nomi kiritilishi shart")
        @Size(max = 128, message = "Kalit nomi 128 belgidan oshmasin")
        String name,

        Set<Permission> scopes,

        Instant expiresAt
) {
}
