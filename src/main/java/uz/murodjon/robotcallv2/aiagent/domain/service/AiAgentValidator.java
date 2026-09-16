package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVoice;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.voice.domain.service.VoiceSettingsValidator;

import java.util.Locale;
import java.util.Set;

/**
 * What an agent may be saved as, whichever screen saved it (NEXT-PLAN A.1). The
 * model-tuning bounds are the same the company-wide settings enforce — an agent overriding
 * them per scenario must not be a way around them; the rest catches what used to surface
 * only on the call: a prompt too long for the model, a data field of a type the extractor
 * cannot fill, a transfer number the trunk cannot dial, a duration the company cap would
 * silently cut anyway.
 */
public final class AiAgentValidator {

    private static final double MAX_TEMPERATURE = 2.0;
    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final int MAX_SYSTEM_PROMPT_CHARS = 12_000;
    private static final Set<String> DATA_FIELD_TYPES = Set.of("string", "number", "boolean", "date", "enum");

    private AiAgentValidator() {
    }

    /**
     * @param companyMaxCallSeconds the company's call cap ({@code AiModelConfig.maxCallSeconds});
     *                              {@code null} or {@code 0} when it has none
     */
    public static void validate(AiAgent agent, Integer companyMaxCallSeconds) {
        Double temperature = agent.temperature();
        if (temperature != null && (temperature < 0 || temperature > MAX_TEMPERATURE)) {
            throw new ValidationException(ErrorCode.AI_AGENT_TEMPERATURE_OUT_OF_RANGE, temperature);
        }
        Integer maxOutputTokens = agent.maxOutputTokens();
        if (maxOutputTokens != null && (maxOutputTokens < 1 || maxOutputTokens > MAX_OUTPUT_TOKENS)) {
            throw new ValidationException(ErrorCode.AI_AGENT_MAX_OUTPUT_TOKENS_OUT_OF_RANGE, maxOutputTokens);
        }
        // The company's voice settings and the agent share one speed range: they used to
        // disagree (4.0 here, 3.0 there), so a speed could be saved that the other refused.
        AiAgentVoice voice = agent.voice();
        VoiceSettingsValidator.validateSpeed(voice.speed());
        voice.perLanguageSpeed().values().forEach(VoiceSettingsValidator::validateSpeed);
        String systemPrompt = agent.script().systemPrompt();
        if (systemPrompt != null && systemPrompt.length() > MAX_SYSTEM_PROMPT_CHARS) {
            throw new ValidationException(ErrorCode.AI_AGENT_SYSTEM_PROMPT_TOO_LONG,
                    systemPrompt.length(), MAX_SYSTEM_PROMPT_CHARS);
        }
        for (DataExtractionField field : agent.dataNeeded()) {
            String type = field.type() == null ? "" : field.type().trim().toLowerCase(Locale.ROOT);
            if (!DATA_FIELD_TYPES.contains(type)) {
                throw new ValidationException(ErrorCode.AI_AGENT_DATA_FIELD_TYPE_INVALID, field.id(), field.type());
            }
        }
        String transferPhone = agent.callBehaviour().transferPhoneNumber();
        if (transferPhone != null && !transferPhone.isBlank() && !PhoneNumbers.isValid(transferPhone)) {
            throw new ValidationException(ErrorCode.AI_AGENT_TRANSFER_PHONE_INVALID, transferPhone);
        }
        Integer maxDuration = agent.limits().maxConversationDurationSeconds();
        if (maxDuration != null && companyMaxCallSeconds != null && companyMaxCallSeconds > 0
                && maxDuration > companyMaxCallSeconds) {
            throw new ValidationException(ErrorCode.AI_AGENT_LIMIT_EXCEEDS_COMPANY_CAP, maxDuration, companyMaxCallSeconds);
        }
    }
}
