package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionBuilder;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentIntegrationTool;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionPressDigit;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionSms;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionSources;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionTransfer;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsContentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTransferMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The function list of an agent that never saved one, derived from what its calls were offering: the
 * ways to hang up, hand over, press keys and text, the tools attached to it and the company's integration
 * tools. Showing it changes nothing about those calls — the runtime keeps reading the older fields until a
 * list is saved.
 */
public final class AgentFunctionDefaults {

    private static final int MAX_ID_CHARS = 40;

    private AgentFunctionDefaults() {
    }

    /** {@code agent}'s own list when it saved one, else the one derived from its older settings. */
    public static List<AgentFunction> resolve(AiAgent agent, AgentFunctionSources sources) {
        return agent.functions() != null ? agent.functions() : derive(agent, sources);
    }

    private static List<AgentFunction> derive(AiAgent agent, AgentFunctionSources sources) {
        AiAgentCallBehaviour behaviour = agent.callBehaviour();
        List<AgentFunction> functions = new ArrayList<>();
        functions.add(system("fn_end_call", AgentFunctionType.END_CALL).build());
        if (behaviour.transferEnabled() || sources.globalOperator()) {
            functions.add(system("fn_transfer_call", AgentFunctionType.TRANSFER_CALL)
                    .transfer(new AgentFunctionTransfer(null, AgentTransferMode.COLD)).build());
        }
        if (behaviour.pressDigitEnabled()) {
            functions.add(system("fn_press_digit", AgentFunctionType.PRESS_DIGIT)
                    .pressDigit(new AgentFunctionPressDigit(null)).build());
        }
        String template = behaviour.midCallSmsTemplate();
        // Without a template the older tool could only refuse, so there is nothing to carry over.
        if (behaviour.midCallSmsEnabled() && template != null && !template.isBlank()) {
            functions.add(system("fn_send_sms", AgentFunctionType.SEND_SMS)
                    .sms(new AgentFunctionSms(AgentSmsContentMode.STATIC, template, null, null, true)).build());
        }
        for (Map.Entry<Long, String> tool : sources.attachedTools().entrySet()) {
            functions.add(AgentFunction.builder().id("fn_tool_" + tool.getKey()).type(AgentFunctionType.CUSTOM)
                    .name(tool.getValue()).toolId(tool.getKey()).build());
        }
        for (AgentIntegrationTool tool : sources.integrationTools()) {
            if (isSwitchedOff(agent, tool)) {
                continue;
            }
            String name = tool.listedName();
            functions.add(AgentFunction.builder().id(integrationId(name)).type(AgentFunctionType.INTEGRATION)
                    .name(name).integrationTool(name).build());
        }
        return List.copyOf(functions);
    }

    /** Whether the agent listed the tool in {@code disabledIntegrationTools}, by its catalog key or the name the model knows. */
    private static boolean isSwitchedOff(AiAgent agent, AgentIntegrationTool tool) {
        for (String entry : agent.disabledIntegrationTools()) {
            String trimmed = entry.trim();
            if (trimmed.equals(tool.key()) || trimmed.equals(tool.toolName())) {
                return true;
            }
        }
        return false;
    }

    private static AgentFunctionBuilder system(String id, AgentFunctionType type) {
        return AgentFunction.builder().id(id).type(type).name(type.systemToolName());
    }

    /** A key made of the tool's name, in the shape the client's own ids have. */
    private static String integrationId(String toolName) {
        String id = "fn_int_" + toolName.replaceAll("[^A-Za-z0-9_-]", "_");
        return id.length() > MAX_ID_CHARS ? id.substring(0, MAX_ID_CHARS) : id;
    }
}
