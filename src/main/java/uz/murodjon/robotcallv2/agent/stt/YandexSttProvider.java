package uz.murodjon.robotcallv2.agent.stt;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import yandex.cloud.api.ai.stt.v3.RecognizerGrpc;
import yandex.cloud.api.ai.stt.v3.Stt;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Yandex SpeechKit speech-to-text over the <b>v3 streaming</b> gRPC API
 * (PROJECT.md §2.4). A bidirectional stream carries the caller's raw 8 kHz
 * LINEAR16_PCM up and interim/final transcripts down, with server-side
 * endpointing (EOU) — no client-side utterance buffering. v3 supports Uzbek
 * ({@code uz-UZ}) as well as {@code ru-RU}.
 *
 * <p>One shared {@link ManagedChannel} to {@code stt.api.cloud.yandex.net:443}
 * (TLS); each call opens its own stream with a fresh {@code x-client-request-id}.
 * Auth is an API key in the {@code authorization: Api-Key ...} gRPC metadata.
 * Registered whenever {@code voice-agent.stt.yandex.api-key} is set — a company picks
 * this provider per-call via {@code ai_agent.stt_provider}, it does
 * not have to be the process-wide {@code voice-agent.stt.provider} default.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.stt.yandex.api-key:}'.isBlank()")
public class YandexSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(YandexSttProvider.class);

    /** The range SpeechKit accepts for the end-of-utterance pause hint (see {@link #clampPauseHint}). */
    private static final int MIN_PAUSE_HINT_MS = 500;
    private static final int MAX_PAUSE_HINT_MS = 5000;

    private final SttProperties sttProperties;
    private final VoiceMetrics metrics;
    private volatile ManagedChannel channel;

    public YandexSttProvider(SttProperties sttProperties, VoiceMetrics metrics) {
        this.sttProperties = sttProperties;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        YandexSttProperties y = sttProperties.yandex();
        if (y == null || y.apiKey() == null || y.apiKey().isBlank()) {
            log.warn("Yandex STT (v3) selected but api-key is blank — recognition will fail");
            return;
        }
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(y.host(), y.port())
                // grpc-java parks a channel with no active RPCs and tears the transport
                // down; the next call then pays a full TCP+TLS handshake. For STT that
                // lands on the first frame of a call — before anyone has spoken.
                .idleTimeout(Long.MAX_VALUE, TimeUnit.DAYS);
        if (y.keepAliveSeconds() > 0) {
            builder.keepAliveTime(y.keepAliveSeconds(), TimeUnit.SECONDS)
                    .keepAliveTimeout(20, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true);
        }
        channel = builder.build();
        // Connect now rather than on the first call's first audio frame.
        channel.getState(true);
        log.info("Yandex STT v3 ready (host={}:{}, sampleRate={}, model='{}', eou={}/{}ms)",
                y.host(), y.port(), y.sampleRate(), y.model(),
                y.eouSensitivity(), y.eouMaxPauseHintMs());
    }

    @Override
    public String name() {
        return "yandex";
    }

    @Override
    public int sampleRate() {
        return sttProperties.yandex().sampleRate();
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing) {
        return startStream(languageCode, alternativeLanguages, listener, externalEndpointing, List.of(), null);
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing,
                                  List<String> hints, String model) {
        ManagedChannel current = channel;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.STT_YANDEX_CHANNEL_UNAVAILABLE, "yandex-stt");
        }
        YandexSttProperties y = sttProperties.yandex();

        // Per-call auth + request-id metadata, attached to a fresh stub.
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Api-Key " + y.apiKey());
        headers.put(Metadata.Key.of("x-client-request-id", Metadata.ASCII_STRING_MARSHALLER),
                UUID.randomUUID().toString());
        if (y.folderId() != null && !y.folderId().isBlank()) {
            headers.put(Metadata.Key.of("x-folder-id", Metadata.ASCII_STRING_MARSHALLER), y.folderId());
        }
        RecognizerGrpc.RecognizerStub stub = RecognizerGrpc.newStub(current)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        // Shared by the two halves of the stream: the response side learns the stream is
        // gone (onError/onCompleted), the request side is what has to stop writing to it.
        AtomicBoolean alive = new AtomicBoolean(true);
        StreamObserver<Stt.StreamingResponse> responseObserver =
                new ResponseHandler(languageCode, listener, alive);
        // The async stub's request side is always flow-control aware; the interface it is
        // declared as just hides it. Held as the wider type so the session can ask whether
        // the transport will actually carry the next frame (SttSession#isReady).
        ClientCallStreamObserver<Stt.StreamingRequest> requestObserver =
                (ClientCallStreamObserver<Stt.StreamingRequest>) stub.recognizeStreaming(responseObserver);

        // First message on the stream: the session options (model, audio format, language).
        requestObserver.onNext(sessionOptions(languageCode, alternativeLanguages, y, externalEndpointing, model));
        log.info("Opened Yandex STT v3 stream for {}{} (endpointing: {})",
                languageCode,
                (alternativeLanguages == null || alternativeLanguages.isEmpty())
                        ? "" : " (+" + String.join(", ", alternativeLanguages) + ")",
                externalEndpointing ? "external" : "speechkit");
        return new YandexSttSession(requestObserver, alive);
    }

    /**
     * Session options for the stream: audio format, which languages may be spoken, and
     * the end-of-utterance detector.
     *
     * <p>The whitelist is a list, not a single code: a bilingual campaign does not know
     * which of its languages the person who picks up speaks, and SpeechKit detects that
     * per sentence using the language restriction whitelist.
     */
    private static Stt.StreamingRequest sessionOptions(String languageCode, List<String> alternativeLanguages,
                                                       YandexSttProperties y, boolean externalEndpointing,
                                                       String requestedModel) {
        Stt.LanguageRestrictionOptions.Builder languages = Stt.LanguageRestrictionOptions.newBuilder()
                .setRestrictionType(Stt.LanguageRestrictionOptions.LanguageRestrictionType.WHITELIST)
                .addLanguageCode(languageCode);
        boolean detecting = false;
        if (alternativeLanguages != null) {
            for (String alternative : alternativeLanguages) {
                if (alternative != null && !alternative.isBlank() && !alternative.equalsIgnoreCase(languageCode)) {
                    languages.addLanguageCode(alternative);
                    detecting = true;
                }
            }
        }
        Stt.RecognitionModelOptions.Builder model = Stt.RecognitionModelOptions.newBuilder()
                .setAudioFormat(Stt.AudioFormatOptions.newBuilder()
                        .setRawAudio(Stt.RawAudio.newBuilder()
                                .setAudioEncoding(Stt.RawAudio.AudioEncoding.LINEAR16_PCM)
                                .setSampleRateHertz(y.sampleRate())
                                .setAudioChannelCount(1)))
                .setLanguageRestriction(languages)
                .setAudioProcessingType(Stt.RecognitionModelOptions.AudioProcessingType.REAL_TIME);
        String chosenModel;
        if (requestedModel != null && !requestedModel.isBlank()) {
            // The agent named the model; a bilingual call does not override that choice,
            // because the whole point of naming it is that this agent knows better than
            // the deployment which model hears its callers.
            chosenModel = requestedModel.trim();
        } else {
            chosenModel = detecting && y.autoDetectModel() != null && !y.autoDetectModel().isBlank()
                    && !"auto".equalsIgnoreCase(y.autoDetectModel())
                    ? y.autoDetectModel()
                    : y.model();
        }
        if (chosenModel != null && !chosenModel.isBlank() && !"auto".equalsIgnoreCase(chosenModel)) {
            model.setModel(chosenModel);
        }
        return Stt.StreamingRequest.newBuilder()
                .setSessionOptions(Stt.StreamingOptions.newBuilder()
                        .setRecognitionModel(model)
                        .setEouClassifier(eouClassifier(y, externalEndpointing)))
                .build();
    }

    /**
     * End-of-utterance detector for the stream (§1.3). Left unset, SpeechKit applies its
     * conservative default and a turn cannot begin until it commits — several hundred
     * milliseconds of every turnaround, spent waiting on silence that already ended.
     *
     * <p>With {@code externalEndpointing} the decision moves to us instead: SpeechKit
     * stops looking for the end of the utterance and finalizes when
     * {@link YandexSttSession#endUtterance()} says so. It is one or the other — an
     * explicit EOU event is ignored unless the classifier was handed over here.
     *
     * <p>Only the cut-off point moves; the recognition model and its accuracy do not.
     */
    private static Stt.EouClassifierOptions eouClassifier(YandexSttProperties y, boolean externalEndpointing) {
        if (externalEndpointing) {
            return Stt.EouClassifierOptions.newBuilder()
                    .setExternalClassifier(Stt.ExternalEouClassifier.newBuilder())
                    .build();
        }
        Stt.DefaultEouClassifier.Builder classifier = Stt.DefaultEouClassifier.newBuilder()
                .setType(y.eouSensitivity() == EouSensitivity.HIGH
                        ? Stt.DefaultEouClassifier.EouSensitivity.HIGH
                        : Stt.DefaultEouClassifier.EouSensitivity.DEFAULT);
        if (y.eouMaxPauseHintMs() > 0) {
            classifier.setMaxPauseBetweenWordsHintMs(clampPauseHint(y.eouMaxPauseHintMs()));
        }
        return Stt.EouClassifierOptions.newBuilder().setDefaultClassifier(classifier).build();
    }

    /**
     * The pause hint SpeechKit will actually accept. Outside {@code [500, 5000]} it does
     * not clamp the value or ignore it — it fails the session with {@code
     * INVALID_ARGUMENT: pause between words hint should be in [500, 5000] millisecond
     * range}, and since the options are sent when the stream opens, the failure lands on
     * every call rather than at startup: the stream dies, reopens, and dies again while a
     * caller is on the line. A tuning value that is one step too aggressive should cost
     * nothing more than the tuning.
     */
    static int clampPauseHint(int hintMs) {
        if (hintMs < MIN_PAUSE_HINT_MS || hintMs > MAX_PAUSE_HINT_MS) {
            int clamped = Math.min(Math.max(hintMs, MIN_PAUSE_HINT_MS), MAX_PAUSE_HINT_MS);
            log.warn("Yandex STT eou-max-pause-hint-ms={} is outside the accepted [{}, {}] range — using {}",
                    hintMs, MIN_PAUSE_HINT_MS, MAX_PAUSE_HINT_MS, clamped);
            return clamped;
        }
        return hintMs;
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

    /** Maps v3 streaming responses (partial / final) onto the {@link TranscriptListener}. */
    private final class ResponseHandler implements StreamObserver<Stt.StreamingResponse> {

        private final String language;
        private final TranscriptListener listener;
        private final AtomicBoolean alive;

        private ResponseHandler(String language, TranscriptListener listener, AtomicBoolean alive) {
            this.language = language;
            this.listener = listener;
            this.alive = alive;
        }

        @Override
        public void onNext(Stt.StreamingResponse response) {
            if (response.hasPartial() && sttProperties.yandex().interimResults()) {
                emit(response.getPartial(), false);
            } else if (response.hasFinal()) {
                emit(response.getFinal(), true);
            } else if (response.hasFinalRefinement()) {
                // Normalized rewrite (punctuation/numbers) of the final we already
                // emitted — NOT a new utterance. Forwarding it too made every client
                // turn reach the dialog twice and wrote duplicate transcript rows.
                log.debug("Yandex STT final refinement ignored ({})", language);
            }
        }

        private void emit(Stt.AlternativeUpdate update, boolean isFinal) {
            if (update.getAlternativesCount() == 0) {
                return;
            }
            Stt.Alternative alt = update.getAlternatives(0);
            String text = alt.getText();
            if (text != null && !text.isBlank()) {
                // Always 0 here — v3's stt.proto marks Alternative.confidence "Currently is
                // not used". The logged "conf=0.0" therefore says nothing about how well the
                // line was recognized; do not read it as a quality signal. Google's provider
                // does populate it, which is why the field stays on the listener.
                listener.onTranscript(text, isFinal, (float) alt.getConfidence());
            }
        }

        @Override
        public void onError(Throwable t) {
            metrics.sttError();
            alive.set(false);
            log.warn("Yandex STT v3 stream error ({}): {}", language, t.getMessage());
        }

        @Override
        public void onCompleted() {
            // Also the server ending a session of its own accord, which for a live call
            // is just as fatal as an error — the bridge reopens either way.
            alive.set(false);
            log.debug("Yandex STT v3 stream completed ({})", language);
        }
    }

    /** Pushes audio chunks into the request stream; completes it on close. */
    private static final class YandexSttSession implements SttSession {

        private final ClientCallStreamObserver<Stt.StreamingRequest> requestObserver;
        private final AtomicBoolean alive;
        private boolean closed;

        private YandexSttSession(ClientCallStreamObserver<Stt.StreamingRequest> requestObserver, AtomicBoolean alive) {
            this.requestObserver = requestObserver;
            this.alive = alive;
        }

        /**
         * Whether the HTTP/2 stream has room for another frame. False means grpc-java
         * would hold the frame in memory instead of writing it — for live audio that is
         * not a delay, it is a loss dressed up as one.
         */
        @Override
        public boolean isReady() {
            return requestObserver.isReady();
        }

        @Override
        public synchronized void sendAudio(byte[] pcm16le) {
            if (closed || !alive.get()) {
                return;
            }
            requestObserver.onNext(Stt.StreamingRequest.newBuilder()
                    .setChunk(Stt.AudioChunk.newBuilder().setData(ByteString.copyFrom(pcm16le)))
                    .build());
        }

        @Override
        public synchronized void endUtterance() {
            if (closed || !alive.get()) {
                return;
            }
            requestObserver.onNext(Stt.StreamingRequest.newBuilder()
                    .setEou(Stt.Eou.newBuilder())
                    .build());
        }

        @Override
        public boolean isAlive() {
            return !closed && alive.get();
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            alive.set(false);
            requestObserver.onCompleted();
        }
    }
}
