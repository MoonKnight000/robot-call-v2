package uz.murodjon.uysotvoice.agent.tts;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import yandex.cloud.api.ai.tts.v3.SynthesizerGrpc;
import yandex.cloud.api.ai.tts.v3.Tts;

import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Yandex SpeechKit text-to-speech over the <b>v3 streaming</b> gRPC API
 * (PROJECT.md §2.5): {@code Synthesizer.UtteranceSynthesis} sends the text in one
 * request (split first if it exceeds {@link #MAX_TEXT_CHARS} — Yandex's own limit)
 * and reads back a stream of raw LINEAR16_PCM audio chunks.
 * {@link #synthesize} buffers and concatenates them into the single {@code short[]}
 * the blocking {@link TtsProvider} contract returns; {@link #synthesizeStreaming}
 * forwards each chunk as it arrives instead, for the caller that can start playing
 * audio before the whole sentence has finished synthesizing. The voice is
 * per-language ({@code voice-agent.tts.yandex.voices}); Uzbek uses the Nigora
 * voice, Russian the default {@code voice}. A campaign that picked a voice passes
 * it in explicitly and overrides both (§2.5).
 *
 * <p>One shared {@link ManagedChannel} to {@code tts.api.cloud.yandex.net:443}
 * (TLS); each call opens its own unary-request/streaming-response call with a
 * fresh {@code x-client-request-id}. Auth is an API key in the
 * {@code authorization: Api-Key ...} gRPC metadata. Created only when
 * {@code voice-agent.tts.yandex.enabled=true} and a key is set.
 */
@Component
@Order(10)
@ConditionalOnProperty(prefix = "voice-agent.tts.yandex", name = "enabled", havingValue = "true")
public class YandexTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(YandexTtsProvider.class);

    /**
     * Longest text Yandex v3 accepts in one {@code UtteranceSynthesis} request — anything
     * longer fails outright with {@code INVALID_ARGUMENT "Too long text"}, which on a live
     * call cost the caller the whole line (and put this provider on the router's cooldown,
     * so the rest of the call went unspoken too). Not a config knob: the limit is Yandex's
     * own contract. It is reachable in normal operation because the number normalizer
     * expands digits before synthesis — "1500000 so'm" arrives as six words — so a long
     * tool-carried reply is split here and synthesized in sequence instead.
     */
    static final int MAX_TEXT_CHARS = 250;

    private final TtsProperties props;
    private volatile ManagedChannel channel;

    public YandexTtsProvider(TtsProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        YandexTtsProperties y = props.yandex();
        if (y.apiKey() == null || y.apiKey().isBlank()) {
            log.warn("Yandex TTS enabled but voice-agent.tts.yandex.api-key is blank — synthesis will fail");
            return;
        }
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(y.host(), y.port())
                // Synthesis is bursty: a call may go a minute between requests, and
                // grpc-java would park the channel and drop the transport. Rebuilding it
                // costs a TCP+TLS handshake — paid inside a live turn, on the one
                // sentence the caller is waiting for.
                .idleTimeout(Long.MAX_VALUE, TimeUnit.DAYS);
        if (y.keepAliveSeconds() > 0) {
            builder.keepAliveTime(y.keepAliveSeconds(), TimeUnit.SECONDS)
                    .keepAliveTimeout(20, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true);
        }
        channel = builder.build();
        // Connect at startup, so the first call of a deploy does not pay the handshake.
        channel.getState(true);
        log.info("Yandex TTS v3 ready (host={}:{}, voice='{}', sampleRate={}, keepAlive={}s)",
                y.host(), y.port(), y.voice(), y.sampleRate(), y.keepAliveSeconds());
    }

    @Override
    public String name() {
        return "yandex";
    }

    @Override
    public boolean supports(String language) {
        // SpeechKit speaks both Uzbek (uz-UZ, Nigora) and Russian — but only with a
        // voice for that language, so require one to be configured.
        return language != null
                && (language.startsWith("ru") || language.startsWith("uz"))
                && !voiceFor(language).isBlank();
    }

    @Override
    public short[] synthesize(String text, String language, String voice) {
        return synthesize(text, language, voice, EffectiveVoiceSettings.NONE);
    }

    @Override
    public short[] synthesize(String text, String language, String voice, EffectiveVoiceSettings style) {
        try {
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            for (String piece : splitForSynthesis(text)) {
                Iterator<Tts.UtteranceSynthesisResponse> responses = openStream(piece, language, voice, style);
                while (responses.hasNext()) {
                    pcm.writeBytes(responses.next().getAudioChunk().getData().toByteArray());
                }
            }
            return toPcm16(pcm.toByteArray());
        } catch (StatusRuntimeException e) {
            throw new ExternalServiceException(ErrorCode.TTS_YANDEX_STREAM_ERROR, "yandex-tts", e, e.getMessage());
        }
    }

    /**
     * As {@link #synthesize}, but delivers each {@code AudioChunk} to {@code onChunk} as
     * it arrives on the wire instead of buffering the whole utterance first — this is
     * what lets the turn's first sentence start playing before Yandex has finished
     * synthesizing it (DialogEngine.speakStreaming, §1.3 turnaround).
     */
    @Override
    public void synthesizeStreaming(String text, String language, String voice, EffectiveVoiceSettings style,
                                     PcmChunkListener onChunk) {
        try {
            for (String piece : splitForSynthesis(text)) {
                Iterator<Tts.UtteranceSynthesisResponse> responses = openStream(piece, language, voice, style);
                while (responses.hasNext()) {
                    byte[] raw = responses.next().getAudioChunk().getData().toByteArray();
                    // Yandex flushes an AudioChunk on synthesis boundaries, not mid-sample, so
                    // (unlike the whole-utterance buffering above) converting chunk by chunk here
                    // is safe — each one is a whole number of 16-bit samples.
                    if (raw.length >= 2) {
                        onChunk.onChunk(toPcm16(raw));
                    }
                }
            }
        } catch (StatusRuntimeException e) {
            throw new ExternalServiceException(ErrorCode.TTS_YANDEX_STREAM_ERROR, "yandex-tts", e, e.getMessage());
        }
    }

    /**
     * Cut {@code text} into pieces the v3 limit accepts, preferring a sentence end, then
     * a word boundary. Nearly every line fits and comes back as the single piece it was;
     * the split only exists so an over-long reply degrades into two sequential requests
     * (heard as a slightly longer pause between them) instead of a lost line.
     */
    static List<String> splitForSynthesis(String text) {
        String rest = text.trim();
        if (rest.length() <= MAX_TEXT_CHARS) {
            return List.of(rest);
        }
        List<String> pieces = new ArrayList<>();
        while (rest.length() > MAX_TEXT_CHARS) {
            int cut = cutPoint(rest);
            pieces.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty()) {
            pieces.add(rest);
        }
        return pieces;
    }

    /** The best split position within the limit: a sentence end, a space, or the hard limit. */
    private static int cutPoint(String text) {
        for (int i = MAX_TEXT_CHARS; i > 0; i--) {
            char c = text.charAt(i - 1);
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                return i;
            }
        }
        for (int i = MAX_TEXT_CHARS; i > 0; i--) {
            if (Character.isWhitespace(text.charAt(i - 1))) {
                return i;
            }
        }
        return MAX_TEXT_CHARS;
    }

    private Iterator<Tts.UtteranceSynthesisResponse> openStream(String text, String language, String voice,
                                                                  EffectiveVoiceSettings style) {
        ManagedChannel current = channel;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.TTS_YANDEX_CHANNEL_UNAVAILABLE, "yandex-tts");
        }
        YandexTtsProperties y = props.yandex();
        String chosen = (voice != null && !voice.isBlank()) ? voice : voiceFor(language);

        Tts.UtteranceSynthesisRequest.Builder request = Tts.UtteranceSynthesisRequest.newBuilder()
                .setText(text)
                .addHints(Tts.Hints.newBuilder().setVoice(chosen))
                .setOutputAudioSpec(Tts.AudioFormatOptions.newBuilder()
                        .setRawAudio(Tts.RawAudio.newBuilder()
                                .setAudioEncoding(Tts.RawAudio.AudioEncoding.LINEAR16_PCM)
                                .setSampleRateHertz(y.sampleRate())));
        // The speaking role of the catalog voice this call picked (tts_voice.role). Sent
        // only when that row declares one: a role belongs to a single voice, and giving a
        // voice a role it does not have fails the request outright rather than being
        // ignored ("role neutral is not supported for voice nigora").
        if (style != null && style.role() != null && !style.role().isBlank()) {
            request.addHints(Tts.Hints.newBuilder().setRole(style.role()));
        }
        // A company's §11 settings/voice override — SpeechKit's own valid range is
        // 0.1-3.0. Pitch has no equivalent hint and is silently not applied.
        if (style != null && style.speed() != null) {
            request.addHints(Tts.Hints.newBuilder().setSpeed(style.speed()));
        }

        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Api-Key " + y.apiKey());
        headers.put(Metadata.Key.of("x-client-request-id", Metadata.ASCII_STRING_MARSHALLER),
                UUID.randomUUID().toString());
        if (y.folderId() != null && !y.folderId().isBlank()) {
            headers.put(Metadata.Key.of("x-folder-id", Metadata.ASCII_STRING_MARSHALLER), y.folderId());
        }
        SynthesizerGrpc.SynthesizerBlockingStub stub = SynthesizerGrpc.newBlockingStub(current)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        return stub.utteranceSynthesis(request.build());
    }

    /**
     * Voice for the requested language: the {@code voices} override for the exact tag,
     * then for its language prefix. The default {@code voice} is a Russian one, so it
     * is only used for ru-RU — other languages need an explicit entry (empty = the
     * language is not supported).
     */
    private String voiceFor(String language) {
        if (language == null) {
            return "";
        }
        Map<String, String> voices = props.yandex().voices();
        if (voices != null && !voices.isEmpty()) {
            String exact = voices.get(language);
            if (exact != null && !exact.isBlank()) {
                return exact;
            }
            String prefix = language.substring(0, Math.min(2, language.length())).toLowerCase();
            for (Map.Entry<String, String> e : voices.entrySet()) {
                if (e.getKey() != null && e.getKey().toLowerCase().startsWith(prefix)
                        && e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        String fallback = props.yandex().voice();
        return language.startsWith("ru") && fallback != null ? fallback : "";
    }

    /** Decode headerless little-endian LPCM bytes into 16-bit samples. */
    private static short[] toPcm16(byte[] lpcm) {
        short[] pcm = new short[lpcm.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            int lo = lpcm[i * 2] & 0xFF;
            int hi = lpcm[i * 2 + 1];
            pcm[i] = (short) ((hi << 8) | lo);
        }
        return pcm;
    }

    @PreDestroy
    public void shutdown() {
        ManagedChannel current = channel;
        if (current != null) {
            current.shutdown();
            try {
                current.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
