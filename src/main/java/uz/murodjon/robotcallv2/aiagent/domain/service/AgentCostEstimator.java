package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

/**
 * The estimate an agent gets before it has made a call: what one minute of talk would use,
 * built from the agent's own prompt and a typical call's rhythm. Spring-free — the numbers
 * are assumptions, so they live where a test can pin them.
 *
 * <p>Priced afterwards by billing's own calculator, so the estimate moves with the rates.
 */
public final class AgentCostEstimator {

    /** Same ratio the reanalysis estimate uses for Uzbek and Russian text. */
    static final int CHARS_PER_TOKEN = 3;
    /** What the prompt factory adds around the agent's text: call frame, guardrails, output rules. */
    static final int FRAMEWORK_PROMPT_TOKENS = 700;
    /** Caller and agent exchanges in a minute of an ordinary call. */
    static final int TURNS_PER_MINUTE = 6;
    /** One exchange as it sits in the history sent with every later turn. */
    static final int HISTORY_TOKENS_PER_TURN = 60;
    /** Turns of history a long call carries — the high end of the token range. */
    static final int LONG_CALL_TURNS = 20;
    static final int COMPLETION_TOKENS_PER_TURN = 40;
    /** About half a minute of the agent's speech. */
    static final int TTS_CHARS_PER_MINUTE = 450;

    private AgentCostEstimator() {
    }

    /** Tokens of instructions every turn starts with: the agent's prompt, its scenario text and the framework. */
    public static int findPromptTokens(AiAgent agent) {
        int chars = 0;
        if (agent.script() != null) {
            chars += length(agent.script().systemPrompt()) + length(agent.script().firstMessage());
            ScenarioDefinition definition = agent.script().definition();
            if (definition != null) {
                chars += length(definition.rolePrompt());
                if (definition.stages() != null) {
                    for (StageDef stage : definition.stages()) {
                        chars += length(stage.purpose());
                    }
                }
            }
        }
        return FRAMEWORK_PROMPT_TOKENS + chars / CHARS_PER_TOKEN;
    }

    /** Tokens of the first turn: instructions plus one reply, no history yet. */
    public static int findLowTokensPerTurn(int promptTokens) {
        return promptTokens + COMPLETION_TOKENS_PER_TURN;
    }

    /** Tokens of a turn deep into a long call, with its history along. */
    public static int findHighTokensPerTurn(int promptTokens) {
        return promptTokens + LONG_CALL_TURNS * HISTORY_TOKENS_PER_TURN + COMPLETION_TOKENS_PER_TURN;
    }

    /**
     * What one minute of talk would use. A speech-to-speech agent is priced by the minute,
     * so only its realtime seconds count; a cascade one pays for the tokens of an average
     * turn, the speech it synthesizes and the line.
     */
    public static CallUsage createOneMinuteUsage(AiAgent agent, int promptTokens) {
        if (isRealtime(agent)) {
            return new CallUsage(60, 0, 0, 0, 0, 60);
        }
        long averageTurnPrompt = (findLowTokensPerTurn(promptTokens) + findHighTokensPerTurn(promptTokens)) / 2
                - COMPLETION_TOKENS_PER_TURN;
        return new CallUsage(60, averageTurnPrompt * TURNS_PER_MINUTE,
                (long) COMPLETION_TOKENS_PER_TURN * TURNS_PER_MINUTE, 0, TTS_CHARS_PER_MINUTE);
    }

    private static boolean isRealtime(AiAgent agent) {
        PipelineMode mode = agent.speechEngine() == null ? null : agent.speechEngine().mode();
        return mode != null && mode != PipelineMode.CASCADE;
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }
}
