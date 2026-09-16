package uz.murodjon.robotcallv2.agent.realtime;

/**
 * Settings for OpenAI Realtime API (WebSocket Bidi audio streaming).
 *
 * @param apiKey               OpenAI API Key
 * @param url                  WebSocket endpoint (default: wss://api.openai.com/v1/realtime)
 * @param model                Model name (e.g. gpt-realtime-2.1, gpt-realtime-2.1-mini)
 * @param voice                Voice name (e.g. alloy, echo, shimmer, ash, ballad, coral, sage, verse)
 * @param connectTimeoutSeconds WebSocket connect timeout
 * @param temperature          Sampling temperature (0.6 - 0.8)
 * @param audioFormat          {@code g711_ulaw} or {@code pcm16}. µ-law is what the phone line
 *                             already carries: the engine takes and returns it at 8 kHz, so
 *                             there is no resampling on either side and a third of the
 *                             bandwidth. {@code pcm16} (24 kHz) only makes sense off the phone
 * @param turnDetection        {@code semantic_vad} — the engine decides the caller has finished
 *                             from what was said, not only from silence, so a caller who pauses
 *                             mid-sentence is not answered half-way — or {@code server_vad}
 *                             (silence only)
 * @param eagerness            {@code semantic_vad} only: {@code low} gives the caller more time,
 *                             {@code high} answers sooner, {@code auto} lets the engine choose
 * @param noiseReduction       {@code far_field} (a phone held away from the mouth, speakerphone —
 *                             the telephone case), {@code near_field} (headset), or blank for none
 * @param transcriptionModel   the model that writes what the caller said into
 *                             {@code call_transcript}. The engine does not need it to converse;
 *                             the transcript, the summary and the CRM note do
 */
public record OpenAiRealtimeProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        int connectTimeoutSeconds,
        double temperature,
        String audioFormat,
        String turnDetection,
        String eagerness,
        String noiseReduction,
        String transcriptionModel
) {
    public OpenAiRealtimeProperties {
        if (url == null || url.isBlank()) {
            url = "wss://api.openai.com/v1/realtime";
        }
        if (model == null || model.isBlank()) {
            model = "gpt-realtime-2.1";
        }
        if (voice == null || voice.isBlank()) {
            voice = "alloy";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
        if (temperature <= 0) {
            temperature = 0.7;
        }
        if (audioFormat == null || audioFormat.isBlank()) {
            audioFormat = "g711_ulaw";
        }
        if (turnDetection == null || turnDetection.isBlank()) {
            turnDetection = "semantic_vad";
        }
        if (eagerness == null || eagerness.isBlank()) {
            eagerness = "auto";
        }
        if (transcriptionModel == null || transcriptionModel.isBlank()) {
            transcriptionModel = "gpt-4o-transcribe";
        }
    }

    /** Whether the session speaks µ-law at the telephone rate rather than 24 kHz PCM. */
    public boolean telephoneAudio() {
        return "g711_ulaw".equalsIgnoreCase(audioFormat);
    }
}
