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
 */
public record GeminiLiveProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        int connectTimeoutSeconds
) {
}
