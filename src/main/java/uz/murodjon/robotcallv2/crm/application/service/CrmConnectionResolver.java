package uz.murodjon.robotcallv2.crm.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

import java.util.Optional;

/**
 * Where one company's CRM traffic goes: its own amoCRM / Kommo / Bitrix24 connection, or
 * Uysot — with the company's OAuth token when it connected one, the platform's static
 * token otherwise. Shared by {@link CrmService} (writes) and {@link CrmCatalogService} (reads).
 */
@Component
class CrmConnectionResolver {

    private static final Logger log = LoggerFactory.getLogger(CrmConnectionResolver.class);

    private final CrmProperties crmProperties;
    private final CrmIntegrationUseCase crmIntegrationUseCase;
    private final CompanyUseCase companyUseCase;

    CrmConnectionResolver(CrmProperties crmProperties, CrmIntegrationUseCase crmIntegrationUseCase,
                          CompanyUseCase companyUseCase) {
        this.crmProperties = crmProperties;
        this.crmIntegrationUseCase = crmIntegrationUseCase;
        this.companyUseCase = companyUseCase;
    }

    /**
     * The connection, or {@code null} when there is nowhere to send to. A connection that
     * cannot be read — a stored secret that no longer decrypts — counts as nowhere, and so
     * does a provider the platform switched off for this company.
     */
    CrmConnection findConnection(long companyId) {
        CrmConnection connection;
        try {
            connection = connectionFor(companyId);
        } catch (RuntimeException e) {
            log.warn("CRM connection of company {} could not be read: {}", companyId, e.getMessage());
            return null;
        }
        if (connection == null) {
            return null;
        }
        if (!companyUseCase.isIntegrationAvailable(companyId, IntegrationProvider.valueOf(connection.provider().name()))) {
            return null;
        }
        return connection;
    }

    private CrmConnection connectionFor(long companyId) {
        Optional<CrmConnection> own = crmIntegrationUseCase.findConnection(companyId);
        if (own.isPresent() && own.get().provider() != CrmProvider.UYSOT) {
            // Chosen but not (or no longer) connected: nothing to call. Falling back to
            // Uysot would write this company's calls into an account that is not theirs.
            return own.get().baseUrl() != null ? own.get() : null;
        }
        if (!isPlatformUysotConfigured()) {
            return null;
        }
        String token = own.map(CrmConnection::accessToken).orElse(null);
        if (token == null && crmProperties.apiToken() != null && !crmProperties.apiToken().isBlank()) {
            token = crmProperties.apiToken();
        }
        return new CrmConnection(CrmProvider.UYSOT, crmProperties.baseUrl(), token);
    }

    /**
     * Whether the platform's Uysot connection is configured. A company's own amoCRM, Kommo
     * or Bitrix24 connection does not depend on it.
     */
    private boolean isPlatformUysotConfigured() {
        return crmProperties.enabled() && crmProperties.baseUrl() != null && !crmProperties.baseUrl().isBlank();
    }
}
