package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionExtract;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionVariable;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsContentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsRecipient;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVariableType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolInputOverride;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolOverride;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What an agent's {@code functions} list may hold: the shape of each function, and the names and ids
 * that must not repeat. Whether a {@code toolId} or an {@code agentTransfer.agentId} is the company's
 * needs a lookup and is the service's.
 *
 * <p>Failures name the function and a reason code ({@code AI_AGENT_FUNCTION_INVALID}); the codes are
 * listed in {@code docs/api/ai-agents.md}.
 */
public final class AgentFunctionValidator {

    static final int MAX_FUNCTIONS = 50;
    static final int MAX_DESCRIPTION_CHARS = 1000;
    static final int MAX_EXECUTION_MESSAGE_CHARS = 300;
    static final int MAX_SMS_TEXT_CHARS = 480;
    static final int MAX_SMS_INSTRUCTION_CHARS = 1000;
    static final int MAX_VARIABLES = 30;
    static final int MAX_VARIABLE_DESCRIPTION_CHARS = 300;
    static final int MAX_CHOICES = 20;
    static final int MAX_CHOICE_CHARS = 100;
    static final int MAX_PAUSE_DETECTION_DELAY_MS = 5000;
    private static final int MAX_INTEGRATION_TOOL_CHARS = 100;
    static final int MAX_INTEGRATION_INPUTS = 30;
    static final int MAX_INTEGRATION_OUTPUTS = 30;
    private static final int MAX_INPUT_NAME_CHARS = 64;

    private static final Pattern FUNCTION_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,63}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*[A-Za-z0-9_.-]{1,64}\\s*}}");
    private static final Pattern TRANSFER_DESTINATION = Pattern.compile(
            "(?i)(sips?:\\S{3,200}|(PJSIP|SIP)/\\S{1,200}|\\+?[0-9]{3,15})");
    private static final Pattern SMS_NUMBER = Pattern.compile("\\+?[0-9]{7,15}");

    /**
     * Model-facing names of the tools that steer the call or are built in: an EXTRACT_VARIABLE function
     * called by one of them would shadow it (compared case-insensitively).
     */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "endcall", "requesthumantransfer", "transfertoagent", "senddtmftones", "sendmidcallsms",
            "recordwrongperson", "recorddonotcall", "waitforcaller", "switchlanguage", "notecallermood",
            "transitionto", "extractvariables", "getfact");

    private AgentFunctionValidator() {
    }

    /**
     * The list as it will be stored: each system function under its fixed name, an INTEGRATION function
     * under its catalog name, and only the settings group of its own type (the others dropped). A CUSTOM
     * function's name is the tool's, which the service fills in after looking the tool up.
     */
    public static List<AgentFunction> normalize(List<AgentFunction> functions) {
        List<AgentFunction> result = new ArrayList<>();
        for (AgentFunction function : functions) {
            AgentFunctionType type = function.type();
            if (type == null) {
                result.add(function);
                continue;
            }
            String name = switch (type) {
                case EXTRACT_VARIABLE -> function.name() == null ? null : function.name().trim();
                case CUSTOM -> function.name();
                case INTEGRATION -> function.integrationTool();
                default -> type.systemToolName();
            };
            result.add(function.toBuilder()
                    .id(function.id() == null ? null : function.id().trim())
                    .name(name)
                    .transfer(type == AgentFunctionType.TRANSFER_CALL ? function.transfer() : null)
                    .agentTransfer(type == AgentFunctionType.AGENT_TRANSFER ? function.agentTransfer() : null)
                    .pressDigit(type == AgentFunctionType.PRESS_DIGIT ? function.pressDigit() : null)
                    .sms(type == AgentFunctionType.SEND_SMS ? function.sms() : null)
                    .extract(type == AgentFunctionType.EXTRACT_VARIABLE ? function.extract() : null)
                    .toolId(type == AgentFunctionType.CUSTOM ? function.toolId() : null)
                    .integrationTool(type == AgentFunctionType.INTEGRATION ? function.integrationTool() : null)
                    .integration(type == AgentFunctionType.INTEGRATION ? function.integration() : null)
                    .build());
        }
        return result;
    }

    /** Throws {@link ValidationException} for the first function that is not usable. Null is "no list" and passes. */
    public static void validate(List<AgentFunction> functions) {
        if (functions == null) {
            return;
        }
        if (functions.size() > MAX_FUNCTIONS) {
            throw invalid("*", "TOO_MANY_FUNCTIONS");
        }
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<AgentFunctionType> systemTypes = new HashSet<>();
        for (AgentFunction function : functions) {
            String id = function.id();
            if (id == null || !FUNCTION_ID.matcher(id).matches()) {
                throw invalid(String.valueOf(id), "ID_INVALID");
            }
            if (!ids.add(id)) {
                throw new ValidationException(ErrorCode.AI_AGENT_FUNCTION_DUPLICATE, id, "id " + id);
            }
            if (function.type() == null) {
                throw invalid(id, "TYPE_REQUIRED");
            }
            if (function.type().isSystem() && !systemTypes.add(function.type())) {
                throw new ValidationException(ErrorCode.AI_AGENT_FUNCTION_DUPLICATE, id, "type " + function.type());
            }
            validateCommon(function);
            validateOfType(function);
            String name = function.name();
            // A CUSTOM function's name arrives with the tool lookup; until then there is nothing to compare.
            if (name != null && !names.add(name.toLowerCase(Locale.ROOT))) {
                throw new ValidationException(ErrorCode.AI_AGENT_FUNCTION_DUPLICATE, id, "name " + name);
            }
        }
    }

    private static void validateCommon(AgentFunction function) {
        String id = function.id();
        if (function.description() != null && function.description().length() > MAX_DESCRIPTION_CHARS) {
            throw invalid(id, "DESCRIPTION_TOO_LONG");
        }
        if (function.executionMessage() != null && function.executionMessage().length() > MAX_EXECUTION_MESSAGE_CHARS) {
            throw invalid(id, "EXECUTION_MESSAGE_TOO_LONG");
        }
    }

    private static void validateOfType(AgentFunction function) {
        String id = function.id();
        switch (function.type()) {
            case TRANSFER_CALL -> {
                String destination = function.transfer() == null ? null : function.transfer().destination();
                if (destination != null && !TRANSFER_DESTINATION.matcher(destination).matches()
                        && !PLACEHOLDER.matcher(destination).matches()) {
                    throw invalid(id, "TRANSFER_DESTINATION_INVALID");
                }
            }
            case AGENT_TRANSFER -> {
                if (function.agentTransfer() == null || function.agentTransfer().agentId() == null) {
                    throw invalid(id, "AGENT_ID_REQUIRED");
                }
            }
            case PRESS_DIGIT -> {
                Integer delay = function.pressDigit() == null ? null : function.pressDigit().pauseDetectionDelayMs();
                if (delay != null && (delay < 0 || delay > MAX_PAUSE_DETECTION_DELAY_MS)) {
                    throw invalid(id, "PAUSE_DELAY_OUT_OF_RANGE");
                }
            }
            case SEND_SMS -> validateSms(function);
            case EXTRACT_VARIABLE -> validateExtract(function);
            case CUSTOM -> {
                if (function.toolId() == null) {
                    throw invalid(id, "TOOL_ID_REQUIRED");
                }
            }
            case INTEGRATION -> {
                String tool = function.integrationTool();
                if (tool == null || tool.length() > MAX_INTEGRATION_TOOL_CHARS) {
                    throw invalid(id, "INTEGRATION_TOOL_REQUIRED");
                }
                validateIntegration(id, function.integration());
            }
            case END_CALL -> {
            }
        }
    }

    private static void validateSms(AgentFunction function) {
        String id = function.id();
        if (function.sms() == null || function.sms().content() == null) {
            throw invalid(id, "SMS_CONTENT_REQUIRED");
        }
        int limit = function.sms().contentMode() == AgentSmsContentMode.STATIC ? MAX_SMS_TEXT_CHARS : MAX_SMS_INSTRUCTION_CHARS;
        if (function.sms().content().length() > limit) {
            throw invalid(id, "SMS_CONTENT_TOO_LONG");
        }
        if (function.sms().sendTo() == AgentSmsRecipient.NUMBER) {
            String number = function.sms().number();
            if (number == null) {
                throw invalid(id, "SMS_NUMBER_REQUIRED");
            }
            if (!SMS_NUMBER.matcher(number).matches() && !PLACEHOLDER.matcher(number).matches()) {
                throw invalid(id, "SMS_NUMBER_INVALID");
            }
        }
    }

    private static void validateExtract(AgentFunction function) {
        String id = function.id();
        String name = function.name();
        if (name == null || !VARIABLE_NAME.matcher(name).matches() || RESERVED_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
            throw invalid(id, "NAME_INVALID");
        }
        AgentFunctionExtract extract = function.extract();
        if (extract == null || extract.variables().isEmpty()) {
            throw invalid(id, "VARIABLES_REQUIRED");
        }
        if (extract.variables().size() > MAX_VARIABLES) {
            throw invalid(id, "TOO_MANY_VARIABLES");
        }
        Set<String> variableNames = new HashSet<>();
        for (AgentFunctionVariable variable : extract.variables()) {
            String variableName = variable.name();
            // "reply" is the line-to-speak parameter every tool carries.
            if (variableName == null || !VARIABLE_NAME.matcher(variableName).matches() || "reply".equalsIgnoreCase(variableName)) {
                throw invalid(id, "VARIABLE_NAME_INVALID");
            }
            if (!variableNames.add(variableName.toLowerCase(Locale.ROOT))) {
                throw new ValidationException(ErrorCode.AI_AGENT_FUNCTION_DUPLICATE, id, "variable " + variableName);
            }
            if (variable.description() != null && variable.description().length() > MAX_VARIABLE_DESCRIPTION_CHARS) {
                throw invalid(id, "VARIABLE_DESCRIPTION_TOO_LONG");
            }
            boolean choicesBroken = variable.choices().size() > MAX_CHOICES
                    || variable.choices().stream().anyMatch(choice -> choice == null || choice.isBlank()
                    || choice.length() > MAX_CHOICE_CHARS)
                    || (variable.type() == AgentVariableType.ENUM && variable.choices().isEmpty());
            if (choicesBroken) {
                throw invalid(id, "VARIABLE_CHOICES_INVALID");
            }
        }
    }

    /**
     * The agent's own inputs and answer fields for an integration tool. Whether an input is one the tool has is
     * not checked here: the catalog may change after the list is saved, and an input it no longer has is ignored.
     */
    private static void validateIntegration(String id, ToolOverride integration) {
        if (integration == null) {
            return;
        }
        if (integration.inputs().size() > MAX_INTEGRATION_INPUTS) {
            throw invalid(id, "INTEGRATION_INPUTS_TOO_MANY");
        }
        Set<String> inputNames = new HashSet<>();
        for (ToolInputOverride input : integration.inputs()) {
            if (input.name() == null || input.name().isEmpty() || input.name().length() > MAX_INPUT_NAME_CHARS
                    || !inputNames.add(input.name()) || input.source() == null) {
                throw invalid(id, "INTEGRATION_INPUT_INVALID");
            }
            if (input.description() != null && input.description().length() > MAX_VARIABLE_DESCRIPTION_CHARS) {
                throw invalid(id, "INTEGRATION_INPUT_INVALID");
            }
            boolean valueMissing = switch (input.source()) {
                case AI -> false;
                case VALUE -> input.value() == null;
                case VARIABLE -> !(input.value() instanceof String variable) || !VARIABLE_NAME.matcher(variable).matches();
            };
            if (valueMissing) {
                throw invalid(id, "INTEGRATION_INPUT_VALUE_REQUIRED");
            }
        }
        if (integration.responseMapping() != null) {
            if (integration.responseMapping().size() > MAX_INTEGRATION_OUTPUTS) {
                throw invalid(id, "INTEGRATION_OUTPUTS_TOO_MANY");
            }
            integration.responseMapping().forEach((name, path) -> {
                boolean pathBroken = path == null || Arrays.stream(path.split("\\|\\|"))
                        .anyMatch(alternative -> !alternative.trim().startsWith("$"));
                if (name == null || !VARIABLE_NAME.matcher(name).matches() || pathBroken) {
                    throw invalid(id, "INTEGRATION_OUTPUT_INVALID");
                }
            });
        }
    }

    private static ValidationException invalid(String functionId, String reason) {
        return new ValidationException(ErrorCode.AI_AGENT_FUNCTION_INVALID, functionId, reason);
    }
}
