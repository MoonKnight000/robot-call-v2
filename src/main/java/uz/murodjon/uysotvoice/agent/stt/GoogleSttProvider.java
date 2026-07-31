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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;

/**
 * Google Cloud Speech-to-Text streaming provider (PROJECT.md §2.4). Uses
 * Application Default Credentials — set {@code GOOGLE_APPLICATION_CREDENTIALS}
 * to a service-account JSON. If the client cannot be created (e.g. no
 * credentials), the app still starts and STT is simply unavailable.
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.stt", name = "provider", havingValue = "google")
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
    public int sampleRate() {
        return props.google().sampleRate();
    }

    @Override
    public SttSession startStream(String languageCode, TranscriptListener listener) {
        SpeechClient current = client;
        if (current == null) {
            throw new ExternalServiceException("google-stt", "client is not available");
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
                log.warn("STT stream error ({}): {}", languageCode, t.getMessage());
            }

            @Override
            public void onComplete() {
                log.debug("STT stream completed ({})", languageCode);
            }
        };

        ClientStream<StreamingRecognizeRequest> stream = current.streamingRecognizeCallable().splitCall(observer);
        // First request carries the config only; audio requests follow.
        stream.send(StreamingRecognizeRequest.newBuilder().setStreamingConfig(streamingConfig).build());
        log.info("Opened Google STT stream for {}", languageCode);
        return new GoogleSttSession(stream);
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

        private GoogleSttSession(ClientStream<StreamingRecognizeRequest> stream) {
            this.stream = stream;
        }

        @Override
        public void sendAudio(byte[] pcm16le) {
            stream.send(StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(ByteString.copyFrom(pcm16le))
                    .build());
        }

        @Override
        public void close() {
            stream.closeSend();
        }
    }
}
