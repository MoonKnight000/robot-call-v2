package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One turn of the studio's "LLM test", through {@link TextTurnEngine} — the turn a call and the
 * chat widget run — so the agent answers with its real prompt, model, persona, handbook, zone
 * and tools. Holds no state: the conversation is rebuilt from what the page sends every turn,
 * as the widget chat rebuilds it from its transcript.
 *
 * <p>MCP tools are left out, as in an evaluation ({@link AgentSimulator}): a test must not
 * create a lead or write a note in a real CRM. The company's REST tools run for real unless
 * {@code toolMocks} answers them.
 */
@Component
public class StudioTestChat {

    /** The same bootstrap a call's greeting turn uses, when the agent has no opening line of its own. */
    private static final String GREETING_BOOTSTRAP =
            "[SYSTEM: The call is connected, the client has picked up. Greet them as planned.]";

    private final AiModelConfigUseCase aiModelConfigUseCase;
    private final CompanyUseCase companyUseCase;
    private final ToolUseCase toolUseCase;
    private final TextTurnEngine textTurnEngine;

    public StudioTestChat(AiModelConfigUseCase aiModelConfigUseCase, CompanyUseCase companyUseCase,
                          ToolUseCase toolUseCase, TextTurnEngine textTurnEngine) {
        this.aiModelConfigUseCase = aiModelConfigUseCase;
        this.companyUseCase = companyUseCase;
        this.toolUseCase = toolUseCase;
        this.textTurnEngine = textTurnEngine;
    }

    /** Whether there is a model to answer with at all. */
    public boolean available() {
        return textTurnEngine.available();
    }

    /**
     * @param history    what was said so far, agent lines as assistant messages
     * @param clientText the client's new line; null — the agent greets
     * @param stage      the stage the previous turn ended in; null — the scenario's start
     * @param outcome    what the agent's tools recorded so far
     * @param variables  the call's dynamic variables (its facts)
     * @param toolMocks  canned tool answers by name; null — the real tools run
     */
    public StudioTestTurn reply(long companyId, AiAgent agent, ScenarioDefinition scenario, List<Message> history,
                                String clientText, String stage, Map<String, Object> outcome,
                                Map<String, Object> variables, Map<String, String> toolMocks) {
        DialogSession dialog = newDialogSession(companyId, agent, scenario, variables, toolMocks);
        if (stage != null && !stage.isBlank()) {
            dialog.setState(stage);
        }
        if (outcome != null) {
            outcome.forEach(dialog::recordOutcome);
        }
        for (Message message : history) {
            dialog.history().add(message);
            if (message instanceof AssistantMessage assistant) {
                dialog.setLastAgentText(assistant.getText());
            }
        }

        String reply;
        List<String> violations = List.of();
        // The preview opens as an outbound call in the agent's own language would.
        String firstMessage = agent.findOpeningLine(agent.language(), false);
        if (clientText == null && firstMessage != null && !firstMessage.isBlank()) {
            // A call says the opening line as written, without asking the model.
            reply = PromptTemplateEngine.render(firstMessage.trim(), dialog.context().facts());
        } else {
            TextTurn turn = textTurnEngine.reply(dialog, clientText == null ? GREETING_BOOTSTRAP : clientText);
            if (turn.isEmpty()) {
                throw new ExternalServiceException(ErrorCode.CHAT_NO_REPLY, "llm");
            }
            reply = turn.line();
            violations = turn.violations();
        }
        boolean ended = dialog.isEnded();
        return new StudioTestTurn(reply, dialog.state(), new LinkedHashMap<>(dialog.outcome()),
                dialog.drainSimulatedToolCalls(), violations, ended,
                ended && dialog.disposition() != null ? dialog.disposition().name() : null);
    }

    /** The session a call of this agent builds, minus audio, MCP and memory. */
    private DialogSession newDialogSession(long companyId, AiAgent agent, ScenarioDefinition scenario,
                                           Map<String, Object> variables, Map<String, String> toolMocks) {
        // A test stays on the platform's model even for a company on its own LLM, as the widget chat does.
        EffectiveAiModelConfig aiModel = aiModelConfigUseCase.findEffectiveByCompanyId(companyId)
                .withoutOwnLlm()
                .withOverrides(agent.llmModel(), agent.temperature(), agent.maxOutputTokens());
        Company company = companyUseCase.findById(companyId);
        Map<String, Object> facts = variables != null ? new LinkedHashMap<>(variables) : Map.of();
        DialogSession dialog = DialogSession.builder("test-" + agent.id(), agent.language())
                .context(new CallContext(facts, null))
                .scenario(scenario)
                .companyId(companyId)
                .disclosureEnabled(false)
                .companyName(company != null ? company.name() : null)
                .aiModel(aiModel)
                .emotionAdaptiveVoice(false)
                .agentPersona(agent.persona())
                .firstMessage(agent.script().firstMessage())
                .tools(toolUseCase.findAgentToolDefinitions(companyId, agent.id()))
                .toolMocks(toolMocks)
                .agent(agent)
                .build();
        dialog.setMcpTools(List.of());
        dialog.setFastModel(agent.fastLlmModel());
        return dialog;
    }
}
