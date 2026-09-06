package uz.murodjon.robotcallv2.aiagent;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.util.Map;
import java.util.Set;

/** A plain enabled agent, for tests that need one but are not testing the agent itself. */
public final class AiAgentFixtures {

    private AiAgentFixtures() {
    }

    public static AiAgent agent(long id, long companyId, long scenarioId, String language, String ttsVoice) {
        return new AiAgent(id, companyId, "Test agent", null, scenarioId, language, ttsVoice,
                Map.of(), AgentPersona.AI_ASSISTANT, null, null, null,
                AmbientSound.OFF, true, false, VoicemailAction.HANGUP, null, false, null,
                Set.of(), true, null, null);
    }
}
