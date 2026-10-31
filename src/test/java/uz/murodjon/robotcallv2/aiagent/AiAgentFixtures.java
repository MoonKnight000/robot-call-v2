package uz.murodjon.robotcallv2.aiagent;

import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.*;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.InterruptionSensitivity;
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
                List.of(language),
                AiAgentChannel.OMNICHANNEL,
                AiAgentMode.SINGLE_PROMPT,
                AgentPersona.AI_ASSISTANT,
                new AiAgentScript(null, scenarioId, ScenarioMode.PROMPT, null, null, null),
                new AiAgentSpeechEngine(PipelineMode.CASCADE, null, null, null, null,
                        "yandex", null, "yandex", null, null, null),
                new AiAgentVoice(ttsVoice, null, Map.of(), Map.of(), true),
                new AiAgentAmbience(AmbientSound.OFF, 0.35, 1.5, AmbientSound.OFF, 0.35,
                        true, NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION),
                AgentGreeting.DEFAULT,
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
                List.of(),
                Set.of(),
                null,
                true,
                null,
                null,
                null,
                0L
        );
    }

    /** {@code agent} speaking as {@code persona}. */
    public static AiAgent withPersona(AiAgent agent, AgentPersona persona) {
        return agent.toBuilder().persona(persona).build();
    }
}
