package uz.murodjon.robotcallv2.integration.domain.service;

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
}
