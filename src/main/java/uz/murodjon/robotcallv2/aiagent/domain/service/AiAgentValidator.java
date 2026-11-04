package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentPhrases;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVoice;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PostCallAction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PostCallCondition;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PostCallActionType;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.scenario.domain.service.ScenarioValidator;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.voice.domain.service.VoiceSettingsValidator;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
    static final int MAX_RAG_TOP_K = 10;
    static final int MAX_RAG_INSTRUCTION_CHARS = 500;
    static final int MAX_WEBHOOK_TIMEOUT_SECONDS = 30;
    static final int MAX_DEFAULT_VARIABLES = 50;
    static final int MAX_DEFAULT_VARIABLE_VALUE_LENGTH = 500;
    static final int MAX_DISABLED_INTEGRATION_TOOLS = 100;
    static final int MAX_INTEGRATION_TOOL_NAME_LENGTH = 100;
    static final int MAX_PHRASE_CHARS = 300;
    static final int MAX_PHRASE_POOL_SIZE = 10;
    static final int MAX_POOL_PHRASE_CHARS = 80;
    static final int MAX_BACKCHANNEL_WORDS = 3;
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final int MAX_SYSTEM_PROMPT_CHARS = 12_000;
    private static final int MAX_ENUM_OPTIONS = 20;
    private static final int MAX_ENUM_OPTION_CHARS = 100;
    private static final int MAX_FORMAT_EXAMPLES = 5;
    private static final int MAX_FORMAT_EXAMPLE_CHARS = 200;
    // Same shape as a contact's custom field key (ContactValidator), which the value is saved under.
    private static final Pattern CONTACT_FIELD_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> DATA_FIELD_TYPES = Set.of("string", "number", "boolean", "date", "enum", "phone", "email");
    private static final int MAX_POST_CALL_CONDITIONS = 10;
    private static final int MAX_CONDITION_VALUE_CHARS = 200;
    private static final Pattern RECIPIENT_PLACEHOLDER = Pattern.compile("\\{\\{\\s*[A-Za-z0-9_.-]{1,64}\\s*}}");
    private static final Pattern TELEGRAM_CHAT_ID = Pattern.compile("-?\\d{1,20}|@[A-Za-z][A-Za-z0-9_]{4,31}");

    private AiAgentValidator() {
    }

    /**
     * @param companyMaxCallSeconds the company's call cap ({@code AiModelConfig.maxCallSeconds});
     *                              {@code null} or {@code 0} when it has none
     */
    public static void validate(AiAgent agent, Integer companyMaxCallSeconds) {
        if (agent.timezone() != null) {
            try {
                //noinspection ResultOfMethodCallIgnored
                ZoneId.of(agent.timezone());
            } catch (DateTimeException e) {
                throw new ValidationException(ErrorCode.AI_AGENT_TIMEZONE_INVALID, agent.timezone());
            }
        }
        if (agent.speechLanguages() != null) {
            for (String lang : agent.speechLanguages()) {
                if (lang != null && !lang.isBlank() && Language.findByCode(lang).isEmpty()) {
                    throw new ValidationException(ErrorCode.LANGUAGE_CODE_INVALID, lang, Language.codes());
                }
            }
        }
        Double temperature = agent.temperature();
        if (temperature != null && (temperature < 0 || temperature > MAX_TEMPERATURE)) {
            throw new ValidationException(ErrorCode.AI_AGENT_TEMPERATURE_OUT_OF_RANGE, temperature);
        }
        Integer maxOutputTokens = agent.maxOutputTokens();
        for (AgentWebhookConfig hook : java.util.Arrays.asList(agent.initiationWebhook(), agent.postCallWebhook())) {
            Integer timeout = hook != null ? hook.timeoutSeconds() : null;
            if (timeout != null && (timeout < 1 || timeout > MAX_WEBHOOK_TIMEOUT_SECONDS)) {
                throw new ValidationException(ErrorCode.AI_AGENT_WEBHOOK_TIMEOUT_OUT_OF_RANGE, MAX_WEBHOOK_TIMEOUT_SECONDS, timeout);
            }
        }
        validateDefaultVariables(agent.defaultVariables());
        validateDisabledIntegrationTools(agent.disabledIntegrationTools());
        validatePhrases(agent.phrases());
        AgentFunctionValidator.validate(agent.functions());
        Integer ragTopK = agent.ragTopK();
        if (ragTopK != null && (ragTopK < 1 || ragTopK > MAX_RAG_TOP_K)) {
            throw new ValidationException(ErrorCode.AI_AGENT_RAG_TOP_K_OUT_OF_RANGE, MAX_RAG_TOP_K, ragTopK);
        }
        Double ragMinScore = agent.ragMinScore();
        if (ragMinScore != null && (ragMinScore.isNaN() || ragMinScore < 0 || ragMinScore > 1)) {
            throw new ValidationException(ErrorCode.AI_AGENT_RAG_MIN_SCORE_OUT_OF_RANGE, ragMinScore);
        }
        String ragInstruction = agent.ragInstruction();
        if (ragInstruction != null && ragInstruction.length() > MAX_RAG_INSTRUCTION_CHARS) {
            throw new ValidationException(ErrorCode.AI_AGENT_RAG_INSTRUCTION_TOO_LONG, MAX_RAG_INSTRUCTION_CHARS, ragInstruction.length());
        }
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
            boolean optionsBroken = field.options().size() > MAX_ENUM_OPTIONS
                    || field.options().stream().anyMatch(option -> option == null || option.isBlank()
                    || option.length() > MAX_ENUM_OPTION_CHARS);
            if (optionsBroken || (!field.options().isEmpty() && !"enum".equals(type))) {
                throw new ValidationException(ErrorCode.AI_AGENT_DATA_FIELD_OPTIONS_INVALID, field.id(),
                        MAX_ENUM_OPTIONS, MAX_ENUM_OPTION_CHARS);
            }
            if (field.formatExamples().size() > MAX_FORMAT_EXAMPLES || field.formatExamples().stream()
                    .anyMatch(example -> example == null || example.isBlank() || example.length() > MAX_FORMAT_EXAMPLE_CHARS)) {
                throw new ValidationException(ErrorCode.AI_AGENT_DATA_FIELD_FORMAT_EXAMPLES_INVALID, field.id(),
                        MAX_FORMAT_EXAMPLES, MAX_FORMAT_EXAMPLE_CHARS);
            }
            if (field.contactField() != null && !CONTACT_FIELD_KEY.matcher(field.contactField()).matches()) {
                throw new ValidationException(ErrorCode.AI_AGENT_DATA_FIELD_CONTACT_FIELD_INVALID, field.id(),
                        field.contactField());
            }
        }
        agent.postCallActions().forEach(AiAgentValidator::validatePostCallAction);
        String transferPhone = agent.callBehaviour().transferPhoneNumber();
        if (transferPhone != null && !transferPhone.isBlank() && !PhoneNumbers.isValid(transferPhone)) {
            throw new ValidationException(ErrorCode.AI_AGENT_TRANSFER_PHONE_INVALID, transferPhone);
        }
        // Same bounds a scenario stage's overrides are held to, so neither can step outside the other.
        Integer responseWaitMs = agent.callBehaviour().responseWaitMs();
        if (responseWaitMs != null && (responseWaitMs < ScenarioValidator.MIN_RESPONSE_WAIT_MS
                || responseWaitMs > ScenarioValidator.MAX_RESPONSE_WAIT_MS)) {
            throw new ValidationException(ErrorCode.AI_AGENT_RESPONSE_WAIT_OUT_OF_RANGE,
                    ScenarioValidator.MIN_RESPONSE_WAIT_MS, ScenarioValidator.MAX_RESPONSE_WAIT_MS, responseWaitMs);
        }
        AiAgentCallBehaviour behaviour = agent.callBehaviour();
        requireInRange("reminderAfterSeconds", behaviour.reminderAfterSeconds(), 1, 300);
        requireInRange("reminderMaxCount", behaviour.reminderMaxCount(), 0, 10);
        requireInRange("dtmfTimeoutMs", behaviour.dtmfTimeoutMs(), 500, 10_000);
        requireInRange("dtmfDigitLimit", behaviour.dtmfDigitLimit(), 1, 50);
        requireInRange("ringDurationSeconds", behaviour.ringDurationSeconds(), 5, 300);
        String terminationKey = behaviour.dtmfTerminationKey();
        if (terminationKey != null && !terminationKey.matches("[0-9#*]")) {
            throw new ValidationException(ErrorCode.AI_AGENT_DTMF_KEY_INVALID, terminationKey);
        }
        List<String> keywords = agent.speechEngine().boostedKeywords();
        if (keywords != null) {
            if (keywords.size() > ScenarioValidator.MAX_BOOSTED_KEYWORDS) {
                throw new ValidationException(ErrorCode.AI_AGENT_BOOSTED_KEYWORDS_TOO_MANY,
                        keywords.size(), ScenarioValidator.MAX_BOOSTED_KEYWORDS);
            }
            for (String keyword : keywords) {
                if (keyword == null || keyword.isBlank() || keyword.length() > ScenarioValidator.MAX_BOOSTED_KEYWORD_LENGTH) {
                    throw new ValidationException(ErrorCode.AI_AGENT_BOOSTED_KEYWORD_INVALID,
                            keyword, ScenarioValidator.MAX_BOOSTED_KEYWORD_LENGTH);
                }
            }
        }
        Integer maxDuration = agent.limits().maxConversationDurationSeconds();
        if (maxDuration != null && companyMaxCallSeconds != null && companyMaxCallSeconds > 0
                && maxDuration > companyMaxCallSeconds) {
            throw new ValidationException(ErrorCode.AI_AGENT_LIMIT_EXCEEDS_COMPANY_CAP, maxDuration, companyMaxCallSeconds);
        }
    }

    /** Conditions, SMS recipient, Telegram target and code — the parts of an action the executor cannot fix on the call. */
    private static void validatePostCallAction(PostCallAction action) {
        boolean conditionsBroken = action.conditions().size() > MAX_POST_CALL_CONDITIONS
                || action.conditions().stream().anyMatch(AiAgentValidator::isConditionBroken);
        if (conditionsBroken) {
            throw new ValidationException(ErrorCode.AI_AGENT_POST_CALL_CONDITION_INVALID, action.id(),
                    MAX_POST_CALL_CONDITIONS, MAX_CONDITION_VALUE_CHARS);
        }
        String recipient = action.smsRecipient();
        if (recipient != null && (action.actionType() != PostCallActionType.SEND_SMS
                || !(PhoneNumbers.isValid(recipient) || RECIPIENT_PLACEHOLDER.matcher(recipient).matches()))) {
            throw new ValidationException(ErrorCode.AI_AGENT_POST_CALL_SMS_RECIPIENT_INVALID, action.id(), recipient);
        }
        Long botId = action.telegramBotId();
        String chatId = action.telegramChatId();
        if (botId != null || chatId != null) {
            boolean targetBroken = action.actionType() != PostCallActionType.TELEGRAM || botId == null || chatId == null
                    || !TELEGRAM_CHAT_ID.matcher(chatId).matches();
            if (targetBroken) {
                throw new ValidationException(ErrorCode.AI_AGENT_POST_CALL_TELEGRAM_TARGET_INVALID, action.id(), chatId);
            }
        }
        boolean isCode = action.actionType() == PostCallActionType.CODE;
        boolean hasCode = action.code() != null;
        if (isCode != hasCode
                || (hasCode && action.code().length() > WorkflowValidator.MAX_CODE_CHARS)) {
            throw new ValidationException(ErrorCode.AI_AGENT_POST_CALL_CODE_INVALID, action.id(),
                    WorkflowValidator.MAX_CODE_CHARS);
        }
    }

    private static boolean isConditionBroken(PostCallCondition condition) {
        if (condition == null || condition.field() == null || condition.field().isBlank()
                || condition.field().length() > 64 || condition.operator() == null) {
            return true;
        }
        if (!condition.operator().needsValue()) {
            return false;
        }
        String value = condition.value();
        if (value == null || value.isBlank() || value.length() > MAX_CONDITION_VALUE_CHARS) {
            return true;
        }
        return condition.operator().isNumeric() && PostCallCondition.parseNumber(value) == null;
    }

    /** Default variable names feed {{name}} placeholders, so they stay simple and bounded. */
    private static void validateDefaultVariables(Map<String, String> variables) {
        if (variables.size() > MAX_DEFAULT_VARIABLES) {
            throw new ValidationException(ErrorCode.AI_AGENT_DEFAULT_VARIABLE_INVALID, "*",
                    MAX_DEFAULT_VARIABLE_VALUE_LENGTH, MAX_DEFAULT_VARIABLES);
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || !VARIABLE_NAME.matcher(name).matches()
                    || value == null || value.length() > MAX_DEFAULT_VARIABLE_VALUE_LENGTH) {
                throw new ValidationException(ErrorCode.AI_AGENT_DEFAULT_VARIABLE_INVALID, name,
                        MAX_DEFAULT_VARIABLE_VALUE_LENGTH, MAX_DEFAULT_VARIABLES);
            }
        }
    }

    /**
     * Names of integration tools the agent switches off. Only their shape is checked: a name the
     * catalog no longer has is ignored at call time, so a renamed tool never blocks saving.
     */
    private static void validateDisabledIntegrationTools(List<String> names) {
        if (names.size() > MAX_DISABLED_INTEGRATION_TOOLS) {
            throw new ValidationException(ErrorCode.AI_AGENT_DISABLED_INTEGRATION_TOOL_INVALID, "*",
                    MAX_INTEGRATION_TOOL_NAME_LENGTH, MAX_DISABLED_INTEGRATION_TOOLS);
        }
        for (String name : names) {
            if (name == null || name.isBlank() || name.length() > MAX_INTEGRATION_TOOL_NAME_LENGTH) {
                throw new ValidationException(ErrorCode.AI_AGENT_DISABLED_INTEGRATION_TOOL_INVALID, name,
                        MAX_INTEGRATION_TOOL_NAME_LENGTH, MAX_DISABLED_INTEGRATION_TOOLS);
            }
        }
    }

    /**
     * The agent's own fixed lines: each short enough to say in one breath, the pools small and
     * made of short lines, and a backchannel no longer than a listening noise — a longer one is
     * heard as the bot cutting in.
     */
    private static void validatePhrases(AiAgentPhrases phrases) {
        requireShortPhrase("stillThere", phrases.stillThere());
        requireShortPhrase("stillThereLastCall", phrases.stillThereLastCall());
        requireShortPhrase("didNotCatch", phrases.didNotCatch());
        requireShortPhrase("farewell", phrases.farewell());
        requireShortPhrase("gratitudeReply", phrases.gratitudeReply());
        requireShortPhrase("doNotCallReply", phrases.doNotCallReply());
        requireSmallPool("thinking", phrases.thinking());
        requireSmallPool("backchannels", phrases.backchannels());
        for (String line : phrases.backchannels()) {
            if (line.split("\\s+").length > MAX_BACKCHANNEL_WORDS) {
                throw new ValidationException(ErrorCode.AI_AGENT_BACKCHANNEL_TOO_LONG, line, MAX_BACKCHANNEL_WORDS);
            }
        }
    }

    private static void requireShortPhrase(String name, String line) {
        if (line != null && line.length() > MAX_PHRASE_CHARS) {
            throw new ValidationException(ErrorCode.AI_AGENT_PHRASE_TOO_LONG, name, MAX_PHRASE_CHARS, line.length());
        }
    }

    private static void requireSmallPool(String name, List<String> lines) {
        boolean tooLong = lines.stream().anyMatch(line -> line.length() > MAX_POOL_PHRASE_CHARS);
        if (lines.size() > MAX_PHRASE_POOL_SIZE || tooLong) {
            throw new ValidationException(ErrorCode.AI_AGENT_PHRASE_POOL_INVALID, name, MAX_PHRASE_POOL_SIZE, MAX_POOL_PHRASE_CHARS);
        }
    }

    /** A tuning number the agent may leave unset (the deployment default), but not set outside its bounds. */
    private static void requireInRange(String name, Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw new ValidationException(ErrorCode.AI_AGENT_CALL_TUNING_OUT_OF_RANGE, name, min, max, value);
        }
    }
}
