package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionSms;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionVariable;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsContentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsRecipient;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVariableType;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolParamDef;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The tools an agent's saved function list adds to a call or words differently, shared by both pipelines:
 * the EXTRACT_VARIABLE tools and the descriptions the model reads. An agent without a saved list is left alone.
 */
final class AgentFunctionTools {

    private AgentFunctionTools() {
    }

    /**
     * One tool per EXTRACT_VARIABLE function, named as the function is: a parameter per variable, each recorded on
     * the call as given — so a later tool can use it as {@code {{name}}} ({@link ToolCallVariables#withOutcome}).
     */
    static List<ToolCallback> buildExtractionTools(AiAgent agent, DialogOutcomeSink session) {
        List<ToolCallback> tools = new ArrayList<>();
        for (AgentFunction function : AgentFunctionPlan.findExtractFunctions(agent)) {
            List<ToolParamDef> params = new ArrayList<>();
            for (AgentFunctionVariable variable : function.extract().variables()) {
                params.add(new ToolParamDef(variable.name(), jsonType(variable.type()), false, describe(variable)));
            }
            String description = function.description() != null ? function.description()
                    : "Records values the conversation has given: "
                    + String.join(", ", function.extract().variables().stream().map(AgentFunctionVariable::name).toList());
            tools.add(ScenarioToolCallbackFactory.build(new ToolDef(function.name(), description, params), session));
        }
        return tools;
    }

    /**
     * {@code callbacks} with the description of each function's tool replaced by the one the agent's function
     * gives it: its own text, and for SEND_SMS the wording its settings call for. A tool no function stands for
     * keeps its description.
     *
     * @param attachedTools the agent's attached tools, to find the tool a CUSTOM function stands for
     */
    static List<ToolCallback> describe(List<ToolCallback> callbacks, AiAgent agent, List<Tool> attachedTools) {
        if (!AgentFunctionPlan.isConfigured(agent)) {
            return callbacks;
        }
        Map<String, AgentFunction> byName = AgentFunctionPlan.indexByModelName(agent, attachedTools);
        List<ToolCallback> described = new ArrayList<>(callbacks.size());
        for (ToolCallback callback : callbacks) {
            AgentFunction function = byName.get(callback.getToolDefinition().name());
            String description = function == null ? null : describedAs(function);
            described.add(description == null ? callback : new DescribedToolCallback(callback, description));
        }
        return described;
    }

    /** The description the model reads for {@code function}, or null to leave the tool's own. */
    private static String describedAs(AgentFunction function) {
        if (function.type() == AgentFunctionType.SEND_SMS && function.sms() != null) {
            return describeSms(function.description(), function.sms());
        }
        return function.description();
    }

    /**
     * What the SMS tool says about itself. The standard text promises the caller's own number, which a function that
     * names another recipient must not; a model-written SMS carries the function's instruction; a silent one says so.
     */
    private static String describeSms(String description, AgentFunctionSms sms) {
        StringBuilder text = new StringBuilder(description != null ? description
                : "Sends an SMS during the call, once per call, at most " + DialogTools.MAX_SMS_CHARS + " characters"
                + (sms.sendTo() == AgentSmsRecipient.NUMBER ? "." : ", to the client's own number."));
        if (sms.contentMode() == AgentSmsContentMode.PROMPT) {
            text.append(" Write the SMS text yourself in the text parameter, following this instruction: ")
                    .append(sms.content());
        }
        if (!sms.speakAfter()) {
            text.append(" Do not tell the client the SMS was sent and write no reply text about it.");
        }
        return text.toString();
    }

    private static String jsonType(AgentVariableType type) {
        return switch (type) {
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case STRING, ENUM -> "string";
        };
    }

    /** What the model reads about a variable: its description, and for a choice the values it may take. */
    private static String describe(AgentFunctionVariable variable) {
        String description = variable.description() == null ? "" : variable.description().trim();
        if (variable.type() != AgentVariableType.ENUM) {
            return description.isEmpty() ? null : description;
        }
        String choices = "exactly one of: " + String.join(", ", variable.choices());
        return description.isEmpty() ? choices : description + " (" + choices + ")";
    }
}
