package uz.murodjon.robotcallv2.aiagent;

import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.*;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A plain enabled agent, for tests that need one but are not testing the agent itself. */
public final class AiAgentFixtures {

    private AiAgentFixtures() {
    }

    public static AiAgent agent(long id, long companyId, long scenarioId, String language, String ttsVoice) {
        return new AiAgent(
                id,
                companyId,
                "Test agent",
                null,
                language,
                AgentPersona.AI_ASSISTANT,
                new AiAgentScript(null, scenarioId, ScenarioMode.PROMPT, null, null, null),
                new AiAgentSpeechEngine(PipelineMode.CASCADE, null, null, null, null,
                        "yandex", null, "yandex", null),
                new AiAgentVoice(ttsVoice, null, Map.of(), Map.of(), true),
                new AiAgentAmbience(AmbientSound.OFF, 0.35, 1.5, AmbientSound.OFF, 0.35,
                        true, NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION),
                new AiAgentCallBehaviour(InterruptionSensitivity.MEDIUM, false,
                        VoicemailAction.LEAVE_MESSAGE, null, false, null, null, null, null),
                new AiAgentLimits(600, 30, 10, 1000),
                new AiAgentDataPolicy(false, true, 90, null),
                null,
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                Set.of(),
                true,
                null,
                null,
                null,
                0L
        );
    }

    /** {@code agent} allowed to dial out over {@code sipTrunkIds} only. */
    public static AiAgent withSipTrunkIds(AiAgent agent, Set<Long> sipTrunkIds) {
        return new AiAgent(agent.id(), agent.companyId(), agent.name(), agent.description(), agent.language(),
                agent.persona(), agent.script(), agent.speechEngine(), agent.voice(), agent.ambience(),
                agent.callBehaviour(), agent.limits(), agent.dataPolicy(), agent.llmModel(), agent.fastLlmModel(),
                agent.temperature(), agent.maxOutputTokens(), agent.useRag(), agent.dataNeeded(),
                agent.dataEvaluation(), agent.initiationWebhook(), agent.postCallWebhook(), agent.pronunciationRules(),
                agent.postCallActions(), sipTrunkIds, agent.enabled(), agent.createdAt(), agent.createdBy(),
                agent.publishedVersion(), agent.version());
    }

    /** {@code agent} speaking as {@code persona}. */
    public static AiAgent withPersona(AiAgent agent, AgentPersona persona) {
        return new AiAgent(agent.id(), agent.companyId(), agent.name(), agent.description(), agent.language(),
                persona, agent.script(), agent.speechEngine(), agent.voice(), agent.ambience(),
                agent.callBehaviour(), agent.limits(), agent.dataPolicy(), agent.llmModel(), agent.fastLlmModel(),
                agent.temperature(), agent.maxOutputTokens(), agent.useRag(), agent.dataNeeded(),
                agent.dataEvaluation(), agent.initiationWebhook(), agent.postCallWebhook(), agent.pronunciationRules(),
                agent.postCallActions(), agent.sipTrunkIds(), agent.enabled(), agent.createdAt(), agent.createdBy(),
                agent.publishedVersion(), agent.version());
    }
}
