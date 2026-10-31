package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentScript;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentSpeechEngine;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCostEstimatorTest {

    @Test
    void theAgentsPromptAddsToTheFrameworkPrompt() {
        AiAgent agent = agent("x".repeat(3000), PipelineMode.CASCADE);

        assertThat(AgentCostEstimator.findPromptTokens(agent))
                .isEqualTo(AgentCostEstimator.FRAMEWORK_PROMPT_TOKENS + 1000);
    }

    @Test
    void anAgentWithoutAScriptStillPaysForTheFramework() {
        AiAgent agent = AiAgent.builder().name("Agent").build();

        assertThat(AgentCostEstimator.findPromptTokens(agent)).isEqualTo(AgentCostEstimator.FRAMEWORK_PROMPT_TOKENS);
    }

    @Test
    void aLongCallCarriesMoreTokensPerTurnThanTheFirstTurn() {
        assertThat(AgentCostEstimator.findHighTokensPerTurn(1000))
                .isGreaterThan(AgentCostEstimator.findLowTokensPerTurn(1000));
    }

    @Test
    void aCascadeMinuteIsPricedOnTokensSpeechAndTheLine() {
        CallUsage usage = AgentCostEstimator.createOneMinuteUsage(agent("prompt", PipelineMode.CASCADE), 1000);

        assertThat(usage.durationSec()).isEqualTo(60);
        assertThat(usage.promptTokens()).isPositive();
        assertThat(usage.completionTokens()).isPositive();
        assertThat(usage.ttsChars()).isPositive();
        assertThat(usage.realtime()).isFalse();
    }

    @Test
    void aRealtimeMinuteIsPricedOnTheEnginesMinute() {
        CallUsage usage = AgentCostEstimator.createOneMinuteUsage(agent("prompt", PipelineMode.REALTIME), 1000);

        assertThat(usage.realtime()).isTrue();
        assertThat(usage.promptTokens()).isZero();
        assertThat(usage.ttsChars()).isZero();
    }

    private static AiAgent agent(String systemPrompt, PipelineMode mode) {
        return AiAgent.builder()
                .name("Agent")
                .script(new AiAgentScript(null, null, null, null, null, systemPrompt))
                .speechEngine(new AiAgentSpeechEngine(mode, null, null, null, null, null, null, null, null, null, null))
                .build();
    }
}
