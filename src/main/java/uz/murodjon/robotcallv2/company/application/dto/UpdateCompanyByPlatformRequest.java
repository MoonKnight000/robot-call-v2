package uz.murodjon.robotcallv2.company.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

import java.time.Instant;
import java.util.Set;

/**
 * Everything the platform edits about a company, in one form: every field is replaced, so a
 * client sends back the company it loaded with the changes made.
 *
 * @param status             ACTIVE, SUSPENDED (blocked) or DELETED. ACTIVE and EXPIRED both mean "not
 *                           closed by the platform" — whether the company is open or expired then
 *                           follows {@code expiresAt}
 * @param expiresAt          when the subscription ends; null for no end date
 * @param maxConcurrentCalls dialer calls the company may hold at once; null for the platform default
 * @param disabledFeatures   switchable products turned off; empty for everything on
 * @param disabledIntegrations integration providers turned off; empty for all on
 */
public record UpdateCompanyByPlatformRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 255) String address,
        @NotNull CompanyStatus status,
        Instant expiresAt,
        @Min(1) @Max(10_000) Integer maxConcurrentCalls,
        @NotNull Set<Feature> disabledFeatures,
        @NotNull Set<IntegrationProvider> disabledIntegrations
) {
}
