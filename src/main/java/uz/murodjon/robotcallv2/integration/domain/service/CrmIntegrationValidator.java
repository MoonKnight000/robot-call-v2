package uz.murodjon.robotcallv2.integration.domain.service;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

public final class CrmIntegrationValidator {

    private CrmIntegrationValidator() {
    }

    public static void validateAppName(String appName) {
        if (appName == null || appName.isBlank()) {
            throw new ValidationException(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED);
        }
    }

    /**
     * amoCRM and Kommo authenticate every request with the account's long-lived token; a
     * Bitrix24 inbound webhook carries its secret in the URL itself. Uysot has neither — it
     * is connected through its OAuth consent screen.
     */
    public static void validateAccount(CrmProvider provider, String accessToken) {
        if (provider == CrmProvider.UYSOT) {
            throw new ValidationException(ErrorCode.CRM_PROVIDER_USES_OAUTH, provider);
        }
        if (provider != CrmProvider.BITRIX24 && (accessToken == null || accessToken.isBlank())) {
            throw new ValidationException(ErrorCode.CRM_ACCESS_TOKEN_REQUIRED, provider);
        }
    }

    /**
     * The address as it is stored: trimmed, {@code https://} in front of a bare domain such
     * as {@code acme.amocrm.ru}, no trailing slash.
     */
    public static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.contains("://") ? normalized : "https://" + normalized;
    }
}
