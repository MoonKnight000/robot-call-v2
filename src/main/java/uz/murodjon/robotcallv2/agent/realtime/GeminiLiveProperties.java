package uz.murodjon.robotcallv2.agent.realtime;

/**
 * Gemini Live settings, bound from {@code voice-agent.realtime.gemini-live.*}
 * (PROJECT.md §12).
 *
 * @param apiKey   Google AI Studio key. Defaults to the same {@code GEMINI_API_KEY} the
 *                 Spring AI chat client uses ({@code spring.ai.google.genai.api-key}) —
 *                 one Gemini account, two ways of talking to it. Blank means the engine
 *                 is not registered at all and REALTIME cannot be selected
 * @param url      the BidiGenerateContent WebSocket endpoint; the key is appended as
 *                 {@code ?key=}
 * @param model    Live model id, sent as {@code models/<model>}. Native-audio models
 *                 speak for themselves; the id moves fast, so it is a setting rather
 *                 than a constant
 * @param voice    prebuilt voice name (e.g. {@code Aoede}, {@code Puck}) used when the
 *                 call's campaign chose none for its language, or blank to leave the model
 *                 on its own default. A campaign's choice wins over this
 * @param connectTimeoutSeconds how long to wait for the socket and the {@code
 *                 setupComplete} that follows it — a call is already ringing while this
 *                 runs, so it cannot be generous
 * @param startOfSpeechSensitivity {@code HIGH} or {@code LOW}: how readily the engine
 *                 decides the caller has started talking — and therefore stops itself.
 *                 {@code LOW} on a phone line, where street noise, a cough or the line's
 *                 own hiss would otherwise cut the bot off mid-sentence. Blank leaves the
 *                 engine's default
 * @param endOfSpeechSensitivity {@code HIGH} or {@code LOW}: how readily it decides the
 *                 caller has finished. {@code HIGH} answers sooner and cuts into longer
 *                 pauses; Uzbek callers pause 1.1–1.8 s mid-sentence (VOICE-QUALITY-PLAN
 *                 C.4), which is where {@code LOW} earns its extra wait. Blank leaves the
 *                 engine's default
 * @param prefixPaddingMs how much speech the engine must hear before a start of speech
 *                 counts; 0 leaves the engine's default
 * @param silenceDurationMs how much silence ends the caller's turn; 0 leaves the engine's
 *                 default
 * @param contextCompressionTriggerTokens when the conversation's context reaches this many
 *                 tokens, the engine drops its oldest turns (sliding window) instead of
 *                 ending the session. A long call with many tool results otherwise runs
 *                 out of context and is closed by the server. 0 leaves compression off
 * @param affectiveDialog let the model hear the caller's tone and answer in kind
 *                 ({@code generationConfig.enableAffectiveDialog}). Native-audio models only;
 *                 off by default because a model or API version that does not know the field
 *                 rejects the whole setup — turn it on and watch the first call connect
 * @param proactiveAudio let the model stay silent on audio that is not addressed to it —
 *                 a television, someone else in the room ({@code proactivity.proactiveAudio}).
 *                 Off by default for the same reason
 */
public record GeminiLiveProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        int connectTimeoutSeconds,
        String startOfSpeechSensitivity,
        String endOfSpeechSensitivity,
        int prefixPaddingMs,
        int silenceDurationMs,
        int contextCompressionTriggerTokens,
        boolean affectiveDialog,
        boolean proactiveAudio
) {
}
