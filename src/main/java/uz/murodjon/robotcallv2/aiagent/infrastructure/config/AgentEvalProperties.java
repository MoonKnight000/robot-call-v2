package uz.murodjon.robotcallv2.aiagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalMode;

/**
 * Agent evaluation policy. Bound from {@code voice-agent.agent-eval.*}.
 *
 * @param publishGate when set, an agent that has test cases is published only if an
 *                    evaluation of its current draft passed every case — {@code TEXT}
 *                    accepts a text or a voice run, {@code VOICE} only a voice run
 *                    (VOICE-PIPELINE-PLAN Bosqich 5). Empty leaves publishing ungated
 */
@ConfigurationProperties(prefix = "voice-agent.agent-eval")
public record AgentEvalProperties(AgentEvalMode publishGate) {
}
