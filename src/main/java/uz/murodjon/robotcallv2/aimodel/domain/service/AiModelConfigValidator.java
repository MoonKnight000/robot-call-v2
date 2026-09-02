package uz.murodjon.robotcallv2.aimodel.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

public final class AiModelConfigValidator {

    private AiModelConfigValidator() {
    }

    public static void validate(Double temperature, Integer maxOutputTokens, Integer maxCallSeconds, Long maxTokensPerCall) {
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "temperature must be between 0.0 and 2.0");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "maxOutputTokens must be positive");
        }
        if (maxCallSeconds != null && maxCallSeconds <= 0) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "maxCallSeconds must be positive");
        }
        if (maxTokensPerCall != null && maxTokensPerCall <= 0) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "maxTokensPerCall must be positive");
        }
    }
}
