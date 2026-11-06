package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentScript;
import uz.murodjon.robotcallv2.aiagent.domain.entity.ImportedAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PostCallAction;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageAction;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDefBuilder;
import uz.murodjon.robotcallv2.scenario.domain.entity.SubflowDef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What an agent export file holds and what an import keeps of it (docs/api/ai-agents.md §14.6).
 *
 * <p>An export is self-contained: a shared scenario the agent points to is written into the
 * file as the agent's own definition, so the file means the same thing on any server. An
 * import drops every reference that only made sense in the exporting company — a queue, an
 * uploaded recording, a company HTTP tool, another agent, an MCP connection, a Telegram bot,
 * knowledge sources —
 * and says what it dropped, because the same numeric id may name something else here.
 */
public final class AiAgentPortability {

    private AiAgentPortability() {
    }

    /**
     * The agent as it goes into a file: its scenario embedded, its identity and ownership blanked.
     *
     * @param sharedScenario the definition of the shared scenario the agent points to; null when it has none
     */
    public static AiAgent toExported(AiAgent agent, ScenarioDefinition sharedScenario) {
        AiAgentScript script = agent.script();
        AiAgentScript ownScript = script.scenarioId() != null && sharedScenario != null
                ? new AiAgentScript(script.templateId(), null, script.mode(), sharedScenario,
                script.firstMessage(), script.systemPrompt())
                : script;
        return agent.toBuilder()
                .id(null)
                .companyId(null)
                .createdBy(null)
                .createdAt(null)
                .script(ownScript)
                .build();
    }

    /** {@code source} as a new draft of {@code companyId}, without the exporting company's references. */
    public static ImportedAgent toImported(AiAgent source, long companyId, String name, Instant now, Long importedBy) {
        List<String> removed = new ArrayList<>();

        AiAgentScript script = source.script();
        if (script.scenarioId() != null) {
            removed.add("script.scenarioId");
        }
        AiAgentScript ownScript = new AiAgentScript(script.templateId(), null, script.mode(),
                withoutCompanyReferences(script.definition(), removed), script.firstMessage(), script.systemPrompt());

        List<AgentFunction> functions = new ArrayList<>();
        for (AgentFunction function : source.functions()) {
            if (function.toolId() != null || function.type() == AgentFunctionType.AGENT_TRANSFER) {
                removed.add("functions." + function.name());
            } else {
                functions.add(function);
            }
        }

        List<PostCallAction> postCallActions = new ArrayList<>();
        for (PostCallAction action : source.postCallActions()) {
            if (action.telegramBotId() != null) {
                removed.add("postCallActions." + action.name());
            } else {
                postCallActions.add(action);
            }
        }

        AiAgentCallBehaviour behaviour = source.callBehaviour();
        if (behaviour.transferQueueId() != null) {
            removed.add("callBehaviour.transferQueueId");
        }
        if (behaviour.preConversationAudioFileId() != null) {
            removed.add("callBehaviour.preConversationAudioFileId");
        }

        AiAgent agent = source.asCopy(name, now, importedBy).toBuilder()
                .companyId(companyId)
                .script(ownScript)
                .functions(functions)
                .callBehaviour(withoutCompanyFiles(behaviour))
                .build()
                .withPostCallActions(postCallActions);
        return new ImportedAgent(agent, removed);
    }

    private static ScenarioDefinition withoutCompanyReferences(ScenarioDefinition definition, List<String> removed) {
        if (definition == null) {
            return null;
        }
        List<SubflowDef> subflows = definition.subflows().stream()
                .map(sf -> new SubflowDef(sf.id(), sf.title(), sf.startStageId(),
                        withoutCompanyReferences(sf.stages(), removed)))
                .toList();
        return definition.toBuilder()
                .stages(withoutCompanyReferences(definition.stages(), removed))
                .subflows(subflows)
                .build();
    }

    private static List<StageDef> withoutCompanyReferences(List<StageDef> stages, List<String> removed) {
        if (stages == null) {
            return null;
        }
        return stages.stream().map(stage -> {
            StageDefBuilder builder = stage.toBuilder();
            if (stage.knowledgeSourceIds() != null && !stage.knowledgeSourceIds().isEmpty()) {
                removed.add("stages." + stage.id() + ".knowledgeSourceIds");
                builder.knowledgeSourceIds(null);
            }
            StageAction action = stage.action();
            if (action != null && (action.targetAgentId() != null || action.mcpConnectionId() != null)) {
                if (action.targetAgentId() != null) {
                    removed.add("stages." + stage.id() + ".action.targetAgentId");
                }
                if (action.mcpConnectionId() != null) {
                    removed.add("stages." + stage.id() + ".action.mcpConnectionId");
                }
                builder.action(new StageAction(action.toolName(), action.transferTo(), action.transferMode(),
                        action.digits(), null, action.smsText(), action.extractFields(), action.code(), null,
                        action.sipHeaders(), action.subflowId()));
            }
            return builder.build();
        }).toList();
    }

    private static AiAgentCallBehaviour withoutCompanyFiles(AiAgentCallBehaviour b) {
        return new AiAgentCallBehaviour(
                b.interruptionSensitivity(),
                b.dtmfInputEnabled(),
                b.voicemailAction(),
                b.voicemailMessage(),
                b.midCallSmsEnabled(),
                b.midCallSmsTemplate(),
                b.transferPhoneNumber(),
                b.transferMessage(),
                null,
                null,
                b.autoEscalateOnFrustration(),
                b.frustrationThreshold(),
                b.responseWaitMs(),
                b.reminderAfterSeconds(),
                b.reminderMaxCount(),
                b.dtmfTimeoutMs(),
                b.dtmfTerminationKey(),
                b.dtmfDigitLimit(),
                b.ringDurationSeconds(),
                b.pressDigitEnabled(),
                b.llmTurnDetection());
    }
}
