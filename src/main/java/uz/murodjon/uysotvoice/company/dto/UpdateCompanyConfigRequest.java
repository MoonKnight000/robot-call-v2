package uz.murodjon.uysotvoice.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

/**
 * @param defaultLanguage     must be a member of {@code supportedLanguages} (backend-
 *                            uchun-talablar.md §13) — used when a campaign/route is
 *                            created without picking one explicitly
 * @param supportedLanguages  every language a campaign or inbound route may declare,
 *                            must not be empty; a blank entry is rejected
 */
public record UpdateCompanyConfigRequest(
        @NotNull LocalTime dialWindowStart,
        @NotNull LocalTime dialWindowEnd,
        @NotBlank String timezone,
        @NotBlank String defaultLanguage,
        @NotEmpty List<@NotBlank String> supportedLanguages
) {
}
