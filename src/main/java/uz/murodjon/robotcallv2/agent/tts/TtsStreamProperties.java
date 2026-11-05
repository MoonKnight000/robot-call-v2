package uz.murodjon.robotcallv2.agent.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How a streamed line is produced and supervised ({@code voice-agent.tts.stream.*}).
 *
 * @param firstChunkTimeoutMs how long a provider may take to produce the first audible audio
 *                            of a streamed line before the call gives up on it and speaks the
 *                            line with the fallback provider; {@code 0} disables the guard.
 *                            A guard against a stalled or dead vendor, not a latency target —
 *                            set it from the {@code voice.tts.ttfa} p95 (with headroom), never
 *                            below it, or healthy slow lines will be failed over too
 * @param yandexStreamSynthesis speak through Yandex's bidirectional {@code StreamSynthesis} instead
 *                            of {@code UtteranceSynthesis}. Off by default: an experiment, to be
 *                            judged by {@code voice.tts.ttfa{provider="yandex"}} against the default
 * @param cartesiaWebSocket   speak through Cartesia's WebSocket ({@code /tts/websocket}, one
 *                            context per line) instead of {@code /tts/bytes}. Off by default
 * @param elevenLabsPreviousText send the reply's previous sentence as {@code previous_text}, so the
 *                            prosody of consecutive sentences joins up. Off by default: ElevenLabs
 *                            does not say which models honour it
 * @param geminiMulaw         ask Gemini for 8 kHz mu-law ({@code generationConfig.responseFormat.audio})
 *                            instead of 24 kHz PCM, so nothing has to be resampled. Off by default: the
 *                            docs do not show a streaming example. A chunk whose {@code mimeType} does
 *                            not say mu-law is handled as before, so a format the model ignores is harmless
 */
@ConfigurationProperties(prefix = "voice-agent.tts.stream")
public record TtsStreamProperties(
        long firstChunkTimeoutMs,
        boolean yandexStreamSynthesis,
        boolean cartesiaWebSocket,
        boolean elevenLabsPreviousText,
        boolean geminiMulaw
) {
}
