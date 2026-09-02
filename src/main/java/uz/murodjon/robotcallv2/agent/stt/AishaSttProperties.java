package uz.murodjon.robotcallv2.agent.stt;

/**
 * Aisha realtime speech-to-text (WebSocket), settings under
 * {@code voice-agent.stt.aisha.*}. A Tashkent provider trained on Uzbek telephone
 * speech and on uz/ru code-switching ("ertaga oplata qilaman"), which is the case the
 * global engines handle worst (PROJECT.md §2.4).
 *
 * <p>The audio format is not a knob: the {@code pcm} stream Aisha accepts is fixed at
 * 16 kHz mono s16le, so {@link AishaSttProvider#sampleRate()} reports that and the call
 * pipeline resamples its 8 kHz telephone audio up to it.
 *
 * @param apiKey         Aisha API key (sent as the {@code token} query parameter — the
 *                       realtime endpoint has no header auth)
 * @param url            realtime WebSocket endpoint; {@code format} and {@code token}
 *                       are appended by the provider
 * @param interimResults deliver partial hypotheses ({@code "partial": true}) in addition
 *                       to finals
 */
public record AishaSttProperties(
        String apiKey,
        String url,
        boolean interimResults
) {
}
