package uz.murodjon.robotcallv2.agent.tts;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;
import yandex.cloud.api.ai.tts.v3.SynthesizerGrpc;
import yandex.cloud.api.ai.tts.v3.Tts;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
 * {@code authorization: Api-Key ...} gRPC metadata. Registered whenever
 * {@code voice-agent.tts.yandex.api-key} is set — a company picks this provider per-call
 * via {@code ai_agent.tts_provider}, it does not have to be the
 * process-wide {@code voice-agent.tts.provider} default.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.tts.yandex.api-key:}'.isBlank()")
public class YandexTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(YandexTtsProvider.class);

    /**
     * Longest text Yandex v3 accepts in one {@code UtteranceSynthesis} request — anything
     * longer fails outright with {@code INVALID_ARGUMENT "Too long text"}, which on a live
     * call cost the caller the whole line. Not a config knob: the limit is Yandex's
     * own contract. It is reachable in normal operation because the number normalizer
     * expands digits before synthesis — "1500000 so'm" arrives as six words — so a long
     * tool-carried reply is split here and synthesized in sequence instead.
     */
    static final int MAX_TEXT_CHARS = 250;

    /**
     * What each mood {@code VoiceEmotionResolver} asks for is called in SpeechKit, best
     * match first. The dialog side names the mood the conversation calls for and knows
     * nothing about voices; this turns that name into a role the chosen voice actually
     * has — the same mood is spelled differently from voice to voice ({@code good} and
     * {@code evil} on the Russian ones). A mood none of a voice's roles covers is spoken
     * without a role rather than refused.
     */
    private static final Map<String, List<String>> ROLE_ALIASES = Map.of(
            "neutral", List.of("neutral"),
            "cheerful", List.of("cheerful", "good", "friendly"),
            "friendly", List.of("friendly", "good", "cheerful"),
            "good", List.of("good", "friendly", "cheerful"),
            "strict", List.of("strict", "evil"),
            "evil", List.of("evil", "strict"),
            "whisper", List.of("whisper"),
            "sad", List.of("sad"));

    /**
     * Longest text one {@code StreamSynthesis} session accepts ("streaming mode" in SpeechKit's
     * limits): twenty times {@link #MAX_TEXT_CHARS}, so a line that had to be cut in pieces for
     * {@code UtteranceSynthesis} goes out whole.
     */
    static final int MAX_STREAM_TEXT_CHARS = 5000;

    /** How long {@code StreamSynthesis} may stay silent when the deployment sets no gRPC deadline. */
    private static final long STREAM_SILENCE_LIMIT_MS = 30_000;

    private final TtsProperties ttsProperties;
    private final TtsStreamProperties streamProperties;
    private volatile ManagedChannel channel;

    public YandexTtsProvider(TtsProperties ttsProperties, TtsStreamProperties streamProperties) {
        this.ttsProperties = ttsProperties;
        this.streamProperties = streamProperties;
    }

    @PostConstruct
    public void init() {
        YandexTtsProperties y = ttsProperties.yandex();
        if (y.apiKey() == null || y.apiKey().isBlank()) {
            log.warn("Yandex TTS selected but voice-agent.tts.yandex.api-key is blank — synthesis will fail");
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

    /**
     * Kept: "+" before a vowel is SpeechKit's TTS markup for stress. Documented for Russian;
     * the Uzbek voices are not documented either way, so check them by ear.
     */
    @Override
    public String applyStressMarks(String text) {
        return text;
    }

    @Override
    public short[] synthesize(String text, String language, String voice) {
        return synthesize(text, language, voice, EffectiveVoiceSettings.NONE);
    }

    @Override
    public short[] synthesize(String text, String language, String voice, EffectiveVoiceSettings style) {
        if (streamProperties.yandexStreamSynthesis()) {
            List<short[]> chunks = new ArrayList<>();
            synthesizeStreaming(text, language, voice, style, chunks::add);
            int total = chunks.stream().mapToInt(chunk -> chunk.length).sum();
            short[] whole = new short[total];
            int offset = 0;
            for (short[] chunk : chunks) {
                System.arraycopy(chunk, 0, whole, offset, chunk.length);
                offset += chunk.length;
            }
            return whole;
        }
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
        if (streamProperties.yandexStreamSynthesis()) {
            for (String piece : splitForSynthesis(text, MAX_STREAM_TEXT_CHARS)) {
                streamSynthesis(piece, language, voice, style, onChunk);
            }
            return;
        }
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
        return splitForSynthesis(text, MAX_TEXT_CHARS);
    }

    /** As {@link #splitForSynthesis(String)}, for a limit other than {@link #MAX_TEXT_CHARS}. */
    static List<String> splitForSynthesis(String text, int limit) {
        String rest = text.trim();
        if (rest.length() <= limit) {
            return List.of(rest);
        }
        List<String> pieces = new ArrayList<>();
        while (rest.length() > limit) {
            int cut = cutPoint(rest, limit);
            pieces.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty()) {
            pieces.add(rest);
        }
        return pieces;
    }

    /** The best split position within the limit: a sentence end, a space, or the hard limit. */
    private static int cutPoint(String text, int limit) {
        for (int i = limit; i > 0; i--) {
            char c = text.charAt(i - 1);
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                return i;
            }
        }
        for (int i = limit; i > 0; i--) {
            if (Character.isWhitespace(text.charAt(i - 1))) {
                return i;
            }
        }
        return limit;
    }

    private Iterator<Tts.UtteranceSynthesisResponse> openStream(String text, String language, String voice,
                                                                  EffectiveVoiceSettings style) {
        ManagedChannel current = channel;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.TTS_YANDEX_CHANNEL_UNAVAILABLE, "yandex-tts");
        }
        YandexTtsProperties y = ttsProperties.yandex();
        String chosen = (voice != null && !voice.isBlank()) ? voice : voiceFor(language);

        Tts.UtteranceSynthesisRequest.Builder request = Tts.UtteranceSynthesisRequest.newBuilder()
                .setText(text)
                .addHints(Tts.Hints.newBuilder().setVoice(chosen))
                .setOutputAudioSpec(Tts.AudioFormatOptions.newBuilder()
                        .setRawAudio(Tts.RawAudio.newBuilder()
                                .setAudioEncoding(Tts.RawAudio.AudioEncoding.LINEAR16_PCM)
                                .setSampleRateHertz(y.sampleRate())));
        // The mood this line is spoken with — dropped when the voice that ends up
        // speaking does not have it (VOICE_ROLES).
        String role = roleFor(chosen, style);
        if (role != null) {
            request.addHints(Tts.Hints.newBuilder().setRole(role));
        }
        // A company's §11 settings/voice override — SpeechKit's own valid range is
        // 0.1-3.0. Pitch has no equivalent hint and is silently not applied.
        if (style != null && style.speed() != null) {
            request.addHints(Tts.Hints.newBuilder().setSpeed(style.speed()));
        }

        SynthesizerGrpc.SynthesizerBlockingStub stub = SynthesizerGrpc.newBlockingStub(current)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders(y)));
        if (y.requestTimeoutMs() > 0) {
            // Covers the whole server stream this returns, so hasNext() is bounded too.
            stub = stub.withDeadlineAfter(y.requestTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        return stub.utteranceSynthesis(request.build());
    }

    /** The per-call gRPC metadata: the API key, a fresh request id and, when set, the folder. */
    private static Metadata authHeaders(YandexTtsProperties y) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Api-Key " + y.apiKey());
        headers.put(Metadata.Key.of("x-client-request-id", Metadata.ASCII_STRING_MARSHALLER),
                UUID.randomUUID().toString());
        if (y.folderId() != null && !y.folderId().isBlank()) {
            headers.put(Metadata.Key.of("x-folder-id", Metadata.ASCII_STRING_MARSHALLER), y.folderId());
        }
        return headers;
    }

    /**
     * One line through {@code Synthesizer.StreamSynthesis}: the options, the text (ending in a
     * space, as the docs ask of every phrase sent this way), a {@code force_synthesis}, then the
     * request side is closed — the server finishes the buffered text and ends the response
     * stream, which is how the end of the line is known (the API has no "last chunk" marker).
     *
     * <p>What this buys over {@code UtteranceSynthesis}: the 5000-character streaming limit
     * instead of 250, so a long line is one request, not several in sequence. What it does not:
     * the options carry no free-form hints, only voice, role and speed. Chunks reach {@code onChunk}
     * on the calling thread, like the unary path, so barge-in and the first-chunk guard behave
     * the same.
     */
    private void streamSynthesis(String text, String language, String voice, EffectiveVoiceSettings style,
                                 PcmChunkListener onChunk) {
        ManagedChannel current = channel;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.TTS_YANDEX_CHANNEL_UNAVAILABLE, "yandex-tts");
        }
        YandexTtsProperties y = ttsProperties.yandex();
        String chosen = (voice != null && !voice.isBlank()) ? voice : voiceFor(language);

        Tts.SynthesisOptions.Builder options = Tts.SynthesisOptions.newBuilder()
                .setVoice(chosen)
                .setOutputAudioSpec(Tts.AudioFormatOptions.newBuilder()
                        .setRawAudio(Tts.RawAudio.newBuilder()
                                .setAudioEncoding(Tts.RawAudio.AudioEncoding.LINEAR16_PCM)
                                .setSampleRateHertz(y.sampleRate())));
        String role = roleFor(chosen, style);
        if (role != null) {
            options.setRole(role);
        }
        if (style != null && style.speed() != null) {
            options.setSpeed(style.speed());
        }

        // A byte[] is a chunk of audio; the END marker is the server closing the stream; a
        // Throwable is the stream failing.
        BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        Object end = new Object();
        StreamObserver<Tts.StreamSynthesisResponse> responses = new StreamObserver<>() {
            @Override
            public void onNext(Tts.StreamSynthesisResponse response) {
                if (response.hasAudioChunk() && !response.getAudioChunk().getData().isEmpty()) {
                    events.add(response.getAudioChunk().getData().toByteArray());
                }
            }

            @Override
            public void onError(Throwable error) {
                events.add(error);
            }

            @Override
            public void onCompleted() {
                events.add(end);
            }
        };
        SynthesizerGrpc.SynthesizerStub stub = SynthesizerGrpc.newStub(current)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders(y)));
        if (y.requestTimeoutMs() > 0) {
            stub = stub.withDeadlineAfter(y.requestTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        long silenceLimitMs = y.requestTimeoutMs() > 0 ? y.requestTimeoutMs() + 2_000L : STREAM_SILENCE_LIMIT_MS;

        StreamObserver<Tts.StreamSynthesisRequest> request = stub.streamSynthesis(responses);
        boolean finished = false;
        try {
            request.onNext(Tts.StreamSynthesisRequest.newBuilder().setOptions(options).build());
            request.onNext(Tts.StreamSynthesisRequest.newBuilder()
                    .setSynthesisInput(Tts.SynthesisInput.newBuilder().setText(text.trim() + " ")).build());
            request.onNext(Tts.StreamSynthesisRequest.newBuilder()
                    .setForceSynthesis(Tts.ForceSynthesisEvent.getDefaultInstance()).build());
            request.onCompleted();
            while (true) {
                Object event = events.poll(silenceLimitMs, TimeUnit.MILLISECONDS);
                if (event == null) {
                    throw new ExternalServiceException(ErrorCode.TTS_YANDEX_STREAM_ERROR, "yandex-tts",
                            "no audio or end of stream for " + silenceLimitMs + " ms");
                }
                if (event == end) {
                    finished = true;
                    return;
                }
                if (event instanceof Throwable error) {
                    throw new ExternalServiceException(ErrorCode.TTS_YANDEX_STREAM_ERROR, "yandex-tts", error,
                            error.getMessage());
                }
                byte[] raw = (byte[]) event;
                // As on the unary path, Yandex flushes a chunk on a synthesis boundary, never mid-sample.
                if (raw.length >= 2) {
                    onChunk.onChunk(toPcm16(raw));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TTS_YANDEX_STREAM_ERROR, "yandex-tts", e, "interrupted");
        } finally {
            if (!finished && request instanceof ClientCallStreamObserver<?> call) {
                call.cancel("synthesis abandoned", null);
            }
        }
    }

    /**
     * The role hint to send for {@code voice}: the mood the turn asked for, expressed as
     * one of the roles that voice declares in {@code voice-agent.tts.yandex.voice-roles},
     * or {@code null} when it has none for it.
     *
     * <p>A role belongs to a single voice, and asking a voice for one it does not have
     * fails the whole request with {@code INVALID_ARGUMENT "role neutral is not supported
     * for voice nigora"} — on a live call that costs the caller the entire line, not just
     * its mood. Which voice ends up speaking is only known here (a call that picked no
     * catalog voice gets the configured per-language default), so this is where the mood
     * is matched against the voice.
     */
    String roleFor(String voice, EffectiveVoiceSettings style) {
        if (voice == null || style == null || style.role() == null || style.role().isBlank()) {
            return null;
        }
        String mood = style.role().trim().toLowerCase(Locale.ROOT);
        Set<String> supported = supportedRoles(voice);
        if (supported.contains(mood)) {
            return mood;
        }
        for (String candidate : ROLE_ALIASES.getOrDefault(mood, List.of())) {
            if (supported.contains(candidate)) {
                return candidate;
            }
        }
        log.debug("Voice '{}' has no role for mood '{}' — speaking the line without one", voice, mood);
        return null;
    }

    /** Roles configured for {@code voice}; empty for a voice that takes none (Nigora). */
    private Set<String> supportedRoles(String voice) {
        Map<String, List<String>> configured = ttsProperties.yandex().voiceRoles();
        if (configured == null || configured.isEmpty()) {
            return Set.of();
        }
        String key = voice.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> e : configured.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().toLowerCase(Locale.ROOT).equals(key)) {
                return e.getValue() == null ? Set.of() : Set.copyOf(e.getValue());
            }
        }
        return Set.of();
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
        Map<String, String> voices = ttsProperties.yandex().voices();
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
        String fallback = ttsProperties.yandex().voice();
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
