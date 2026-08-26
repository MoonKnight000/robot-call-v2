package uz.murodjon.uysotvoice.agent.stt;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Google Cloud Speech-to-Text streaming provider (PROJECT.md §2.4). Uses
 * Application Default Credentials — set {@code GOOGLE_APPLICATION_CREDENTIALS}
 * to a service-account JSON. If the client cannot be created (e.g. no
 * credentials), the app still starts and STT is simply unavailable.
 *
 * <p>Registered whenever {@code GOOGLE_APPLICATION_CREDENTIALS} is set — a company picks
 * this provider per-call via {@code engine_config.stt_provider} (§11 settings), it does
 * not have to be the process-wide {@code voice-agent.stt.provider} default.
 */
@Component
@ConditionalOnExpression("!'${GOOGLE_APPLICATION_CREDENTIALS:}'.isBlank()")
public class GoogleSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleSttProvider.class);

    private final SttProperties props;
    private final VoiceMetrics metrics;
    private volatile SpeechClient client;

    public GoogleSttProvider(SttProperties props, VoiceMetrics metrics) {
        this.props = props;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        try {
            client = SpeechClient.create();
            log.info("Google STT ready (model='{}', sampleRate={})",
                    props.google().model(), props.google().sampleRate());
        } catch (Exception e) {
            // Non-fatal: run without STT until credentials are configured.
            log.error("Google STT unavailable (check GOOGLE_APPLICATION_CREDENTIALS): {}", e.getMessage());
        }
    }

    @Override
    public String name() {
        return "google";
    }

    @Override
    public int sampleRate() {
        return props.google().sampleRate();
    }

    @Override
    public SttSession startStream(String languageCode, TranscriptListener listener, boolean externalEndpointing) {
        SpeechClient current = client;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.STT_GOOGLE_CLIENT_UNAVAILABLE, "google-stt");
        }
        if (externalEndpointing) {
            // Google's streaming API has no equivalent of SpeechKit's external EOU
            // classifier: its own endpointer always decides. Saying so beats letting the
            // setting look effective while nothing about the timing changes.
            log.debug("Google STT has no external endpointing — its own endpointer stays in charge");
        }

        RecognitionConfig.Builder recConfig = RecognitionConfig.newBuilder()
                .setEncoding(AudioEncoding.LINEAR16)
                .setSampleRateHertz(props.google().sampleRate())
                .setLanguageCode(languageCode)
                .setEnableAutomaticPunctuation(props.google().enablePunctuation());
        String model = props.google().model();
        if (model != null && !model.isBlank()) {
            recConfig.setModel(model);
        }

        StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                .setConfig(recConfig.build())
                .setInterimResults(true)
                .setSingleUtterance(false)
                .build();

        // Shared with the session below so a stream torn down under a live call (Google
        // caps a streaming recognize at a few minutes) is visible to the side that writes
        // audio, instead of every later frame vanishing silently.
        AtomicBoolean alive = new AtomicBoolean(true);
        ResponseObserver<StreamingRecognizeResponse> observer = new ResponseObserver<>() {
            @Override
            public void onStart(StreamController controller) {
                // no-op
            }

            @Override
            public void onResponse(StreamingRecognizeResponse response) {
                for (StreamingRecognitionResult result : response.getResultsList()) {
                    if (result.getAlternativesCount() == 0) {
                        continue;
                    }
                    SpeechRecognitionAlternative alt = result.getAlternatives(0);
                    listener.onTranscript(alt.getTranscript(), result.getIsFinal(), alt.getConfidence());
                }
            }

            @Override
            public void onError(Throwable t) {
                metrics.sttError();
                alive.set(false);
                log.warn("STT stream error ({}): {}", languageCode, t.getMessage());
            }

            @Override
            public void onComplete() {
                alive.set(false);
                log.debug("STT stream completed ({})", languageCode);
            }
        };

        ClientStream<StreamingRecognizeRequest> stream = current.streamingRecognizeCallable().splitCall(observer);
        // First request carries the config only; audio requests follow.
        stream.send(StreamingRecognizeRequest.newBuilder().setStreamingConfig(streamingConfig).build());
        log.info("Opened Google STT stream for {}", languageCode);
        return new GoogleSttSession(stream, alive);
    }

    @PreDestroy
    public void shutdown() {
        SpeechClient current = client;
        if (current != null) {
            current.close();
        }
    }

    /** Wraps a bidirectional streaming call as an {@link SttSession}. */
    private static final class GoogleSttSession implements SttSession {

        private final ClientStream<StreamingRecognizeRequest> stream;
        private final AtomicBoolean alive;

        private GoogleSttSession(ClientStream<StreamingRecognizeRequest> stream, AtomicBoolean alive) {
            this.stream = stream;
            this.alive = alive;
        }

        @Override
        public void sendAudio(byte[] pcm16le) {
            if (!alive.get()) {
                return;
            }
            stream.send(StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(ByteString.copyFrom(pcm16le))
                    .build());
        }

        @Override
        public boolean isAlive() {
            return alive.get();
        }

        @Override
        public void close() {
            alive.set(false);
            stream.closeSend();
        }
    }
}
