package uz.murodjon.uysotvoice.agent.stt;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import yandex.cloud.api.ai.stt.v3.RecognizerGrpc;
import yandex.cloud.api.ai.stt.v3.Stt;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
 * Selected via {@code voice-agent.stt.provider=yandex} (the default here).
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.stt", name = "provider", havingValue = "yandex", matchIfMissing = true)
public class YandexSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(YandexSttProvider.class);

    private final SttProperties props;
    private final VoiceMetrics metrics;
    private volatile ManagedChannel channel;

    public YandexSttProvider(SttProperties props, VoiceMetrics metrics) {
        this.props = props;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        SttProperties.Yandex y = props.yandex();
        if (y == null || y.apiKey() == null || y.apiKey().isBlank()) {
            log.warn("Yandex STT (v3) selected but api-key is blank — recognition will fail");
            return;
        }
        channel = ManagedChannelBuilder.forAddress(y.host(), y.port()).build();
        log.info("Yandex STT v3 ready (host={}:{}, sampleRate={}, model='{}')",
                y.host(), y.port(), y.sampleRate(), y.model());
    }

    @Override
    public int sampleRate() {
        return props.yandex().sampleRate();
    }

    @Override
    public SttSession startStream(String languageCode, TranscriptListener listener) {
        ManagedChannel current = channel;
        if (current == null) {
            throw new IllegalStateException("Yandex STT v3 channel is not available (check STT_YANDEX_API_KEY)");
        }
        SttProperties.Yandex y = props.yandex();

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

        StreamObserver<Stt.StreamingResponse> responseObserver = new ResponseHandler(languageCode, listener);
        StreamObserver<Stt.StreamingRequest> requestObserver = stub.recognizeStreaming(responseObserver);

        // First message on the stream: the session options (model, audio format, language).
        requestObserver.onNext(sessionOptions(languageCode, y));
        log.info("Opened Yandex STT v3 stream for {}", languageCode);
        return new YandexSttSession(requestObserver);
    }

    private static Stt.StreamingRequest sessionOptions(String languageCode, SttProperties.Yandex y) {
        Stt.RecognitionModelOptions.Builder model = Stt.RecognitionModelOptions.newBuilder()
                .setAudioFormat(Stt.AudioFormatOptions.newBuilder()
                        .setRawAudio(Stt.RawAudio.newBuilder()
                                .setAudioEncoding(Stt.RawAudio.AudioEncoding.LINEAR16_PCM)
                                .setSampleRateHertz(y.sampleRate())
                                .setAudioChannelCount(1)))
                .setLanguageRestriction(Stt.LanguageRestrictionOptions.newBuilder()
                        .setRestrictionType(Stt.LanguageRestrictionOptions.LanguageRestrictionType.WHITELIST)
                        .addLanguageCode(languageCode))
                .setAudioProcessingType(Stt.RecognitionModelOptions.AudioProcessingType.REAL_TIME);
        if (y.model() != null && !y.model().isBlank()) {
            model.setModel(y.model());
        }
        return Stt.StreamingRequest.newBuilder()
                .setSessionOptions(Stt.StreamingOptions.newBuilder().setRecognitionModel(model))
                .build();
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

        private ResponseHandler(String language, TranscriptListener listener) {
            this.language = language;
            this.listener = listener;
        }

        @Override
        public void onNext(Stt.StreamingResponse response) {
            if (response.hasPartial() && props.yandex().interimResults()) {
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
                listener.onTranscript(text, isFinal, (float) alt.getConfidence());
            }
        }

        @Override
        public void onError(Throwable t) {
            metrics.sttError();
            log.warn("Yandex STT v3 stream error ({}): {}", language, t.getMessage());
        }

        @Override
        public void onCompleted() {
            log.debug("Yandex STT v3 stream completed ({})", language);
        }
    }

    /** Pushes audio chunks into the request stream; completes it on close. */
    private static final class YandexSttSession implements SttSession {

        private final StreamObserver<Stt.StreamingRequest> requestObserver;
        private boolean closed;

        private YandexSttSession(StreamObserver<Stt.StreamingRequest> requestObserver) {
            this.requestObserver = requestObserver;
        }

        @Override
        public synchronized void sendAudio(byte[] pcm16le) {
            if (closed) {
                return;
            }
            requestObserver.onNext(Stt.StreamingRequest.newBuilder()
                    .setChunk(Stt.AudioChunk.newBuilder().setData(ByteString.copyFrom(pcm16le)))
                    .build());
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            requestObserver.onCompleted();
        }
    }
}
