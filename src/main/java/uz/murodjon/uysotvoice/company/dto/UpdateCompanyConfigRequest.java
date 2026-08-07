package uz.murodjon.uysotvoice.company.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.company.enums.Language;
import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.LocalTime;
import java.util.List;

/**
 * @param defaultLanguage     must be a member of {@code supportedLanguages} (backend-
 *                            uchun-talablar.md §13) — used when a campaign/route is
 *                            created without picking one explicitly
 * @param supportedLanguages  every language a campaign or inbound route may declare,
 *                            must not be empty — a closed set (report #6): an unknown
 *                            code (anything other than {@code uz-UZ}/{@code ru-RU}/
 *                            {@code en-US}) is rejected at deserialization, before
 *                            validation even runs
 * @param disclosureText      the §11.1 notice this company's calls open with. It must
 *                            still say the call is automated and recorded — a text that
 *                            does not is rejected (§11.1 is not negotiable, see {@code
 *                            shared.dialog.Disclosure}). {@code {company}} is replaced
 *                            with the company's name; null/blank restores the
 *                            platform's own wording
 */
public record UpdateCompanyConfigRequest(
        @NotNull @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @NotNull @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        @NotBlank String timezone,
        @NotNull Language defaultLanguage,
        @NotEmpty List<@NotNull Language> supportedLanguages,
        String disclosureText
) {
}
