package uz.murodjon.robotcallv2.aimodel.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

public final class AiModelConfigValidator {

    private AiModelConfigValidator() {
    }

    public static void validate(Double temperature, Integer maxOutputTokens, Integer maxCallSeconds, Long maxTokensPerCall) {
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new ValidationException(ErrorCode.AI_MODEL_TEMPERATURE_OUT_OF_RANGE, temperature);
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new ValidationException(ErrorCode.AI_MODEL_MAX_OUTPUT_TOKENS_NOT_POSITIVE, maxOutputTokens);
        }
        if (maxCallSeconds != null && maxCallSeconds <= 0) {
            throw new ValidationException(ErrorCode.AI_MODEL_MAX_CALL_SECONDS_NOT_POSITIVE, maxCallSeconds);
        }
        if (maxTokensPerCall != null && maxTokensPerCall <= 0) {
            throw new ValidationException(ErrorCode.AI_MODEL_MAX_TOKENS_PER_CALL_NOT_POSITIVE, maxTokensPerCall);
        }
    }
}
