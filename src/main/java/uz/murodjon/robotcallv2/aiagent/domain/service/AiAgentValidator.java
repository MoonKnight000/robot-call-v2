package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/**
 * The model-tuning bounds an agent may set for itself. Same limits the company-wide
 * settings enforce — an agent overriding them per scenario must not be a way around them.
 */
public final class AiAgentValidator {

    private static final double MAX_TEMPERATURE = 2.0;
    private static final int MAX_OUTPUT_TOKENS = 4096;

    private AiAgentValidator() {
    }

    public static void validate(Double temperature, Integer maxOutputTokens) {
        if (temperature != null && (temperature < 0 || temperature > MAX_TEMPERATURE)) {
            throw new ValidationException(ErrorCode.AI_AGENT_TEMPERATURE_OUT_OF_RANGE, temperature);
        }
        if (maxOutputTokens != null && (maxOutputTokens < 1 || maxOutputTokens > MAX_OUTPUT_TOKENS)) {
            throw new ValidationException(ErrorCode.AI_AGENT_MAX_OUTPUT_TOKENS_OUT_OF_RANGE, maxOutputTokens);
        }
    }
}
