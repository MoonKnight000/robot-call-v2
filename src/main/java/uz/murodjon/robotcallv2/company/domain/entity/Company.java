package uz.murodjon.robotcallv2.company.domain.entity;

import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain record for company (ROADMAP B.1).
 *
 * @param maxConcurrentCalls how many dialer calls the company may hold at once, as its plan allows;
 *                           null for the platform default
 * @param disabledFeatures   the products the platform has switched off for this company; empty when
 *                           everything is on
 * @param disabledIntegrations the integrations the platform has switched off for this company; empty when
 *                            all are on
 * @param expiresAt          when the platform-set subscription ends; null for no end date
 */
public record Company(
        long id,
        String name,
        CompanyStatus status,
        Instant createdAt,
        Long logoFileId,
        String address,
        Integer maxConcurrentCalls,
        Set<Feature> disabledFeatures,
        Set<IntegrationProvider> disabledIntegrations,
        Instant expiresAt
) {

    public Company {
        disabledFeatures = disabledFeatures == null ? Set.of() : Set.copyOf(disabledFeatures);
        disabledIntegrations = disabledIntegrations == null ? Set.of() : Set.copyOf(disabledIntegrations);
    }

    /** A brand-new tenant: everything but the name is the storage layer's to fill in. */
    public static Company named(String name) {
        return builder().name(name).build();
    }

    /** The editable profile of an existing tenant, for a write. */
    public static Company profile(String name, String address) {
        return builder().name(name).address(address).build();
    }

    public static CompanyBuilder builder() {
        return new CompanyBuilder();
    }

    public CompanyBuilder toBuilder() {
        return new CompanyBuilder(this);
    }

    /** Open for business: not suspended, expired or deleted by the platform. */
    public boolean isOpen() {
        return status == CompanyStatus.ACTIVE;
    }

    /** The one rule every entry point asks: an open company with the feature switched on. */
    public boolean mayUse(Feature feature) {
        return isOpen() && isEnabled(feature);
    }

    /** A provider needs the integrations feature as well as its own switch. */
    public boolean mayUse(IntegrationProvider provider) {
        return mayUse(Feature.INTEGRATIONS) && isEnabled(provider);
    }

    public boolean isEnabled(IntegrationProvider provider) {
        return !disabledIntegrations.contains(provider);
    }

    public boolean isEnabled(Feature feature) {
        return !disabledFeatures.contains(feature);
    }

    /** What is left of {@code permissions} once those of the switched-off features are taken away. */
    public Set<Permission> retainEnabled(Set<Permission> permissions) {
        if (disabledFeatures.isEmpty()) {
            return permissions;
        }
        return permissions.stream()
                .filter(permission -> isEnabled(permission.feature()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
