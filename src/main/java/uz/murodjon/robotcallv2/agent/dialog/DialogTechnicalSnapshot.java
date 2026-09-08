package uz.murodjon.robotcallv2.agent.dialog;

/**
 * What a conversation accumulated for the "Texnik" tab (§10.5), read once at teardown
 * before the dialog session is dropped — same reason as {@link DialogOutcome}: by the
 * time teardown runs, the session is gone.
 *
 * <p>{@code ttsVoice}/{@code language} are carried through rather than the resolved TTS
 * provider name itself, because resolving the catalog id to a provider is a lookup the
 * writer (which already has the catalog) can do at persist time — no need to duplicate
 * that logic here.
 *
 * @param turnCount         number of LLM turns this call ran
 * @param promptTokens      prompt tokens spent, summed across every turn
 * @param completionTokens  completion tokens spent (includes Gemini's thinking tokens)
 * @param cachedTokens      of {@code promptTokens}, how many were billed at the cached rate
 * @param ttsChars          characters this call asked a synthesizer for, for billing
 * @param avgTurnLatencyMs  average client-stopped-talking -> first-audio-queued latency, or null if no turn completed
 * @param maxTurnLatencyMs  the slowest one
 * @param avgLlmLatencyMs   average LLM-only wall time per turn, or null if no turn completed
 * @param maxLlmLatencyMs   the slowest one
 * @param ttsVoice          catalog voice id the call actually spoke with, for resolving the TTS provider at write time
 * @param language          the call's language, for the same TTS resolution
 * @param engine            the speech-to-speech engine that ran the call ({@code gemini-live},
 *                          {@code openai-realtime}, …), or null when it ran on the cascade
 *                          pipeline. A realtime engine does recognition, reasoning and speech
 *                          itself, so it is the answer to all three — the agent's cascade
 *                          STT/TTS providers were never used and must not be reported
 * @param llmModel          the model this call actually ran on, or null when it is not known
 */
public record DialogTechnicalSnapshot(
        int turnCount,
        long promptTokens, long completionTokens, long cachedTokens,
        long ttsChars,
        Integer avgTurnLatencyMs, Integer maxTurnLatencyMs,
        Integer avgLlmLatencyMs, Integer maxLlmLatencyMs,
        String ttsVoice, String language,
        String engine, String llmModel) {

    public static final DialogTechnicalSnapshot NONE =
            new DialogTechnicalSnapshot(0, 0, 0, 0, 0, null, null, null, null, null, null, null, null);
}
