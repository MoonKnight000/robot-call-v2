package uz.murodjon.robotcallv2.voice.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.tts.ElevenLabsTtsProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.voice.application.port.output.VoiceClonerPort;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * ElevenLabs instant voice cloning ({@code POST /v1/voices/add}, {@code DELETE /v1/voices/{id}}).
 * The clone lands in the platform's ElevenLabs account and is spoken by {@code ElevenLabsTtsProvider}
 * with the same key.
 */
@Component
public class ElevenLabsVoiceClonerAdapter implements VoiceClonerPort {

    private static final String SERVICE = "elevenlabs-tts";
    /** Uploading a few minutes of audio and training the clone takes longer than a synthesis call. */
    private static final Duration CLONE_TIMEOUT = Duration.ofSeconds(60);

    private final TtsProperties ttsProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ElevenLabsVoiceClonerAdapter(TtsProperties ttsProperties, ObjectMapper objectMapper) {
        this.ttsProperties = ttsProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String findProviderName() {
        return "elevenlabs";
    }

    @Override
    public boolean isAvailable() {
        return !findApiKey().isBlank();
    }

    @Override
    public String createClone(String name, List<UploadedFile> samples) {
        String boundary = "----robotcall" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "name", name);
        writeField(body, boundary, "remove_background_noise", "true");
        for (UploadedFile sample : samples) {
            String filename = sample.originalFilename() != null ? sample.originalFilename() : "sample";
            writeText(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
                    + filename.replace("\"", "") + "\"\r\nContent-Type: " + sample.contentType() + "\r\n\r\n");
            body.writeBytes(sample.bytes());
            writeText(body, "\r\n");
        }
        writeText(body, "--" + boundary + "--\r\n");
        HttpRequest request = HttpRequest.newBuilder(voicesUri("/v1/voices/add"))
                .header("xi-api-key", findApiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(CLONE_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ExternalServiceException(ErrorCode.VOICE_CLONE_FAILED, SERVICE,
                        "HTTP " + response.statusCode() + " " + response.body());
            }
            String voiceId = objectMapper.readTree(response.body()).path("voice_id").asText("");
            if (voiceId.isBlank()) {
                throw new ExternalServiceException(ErrorCode.VOICE_CLONE_FAILED, SERVICE, "no voice_id in the answer");
            }
            return voiceId;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.VOICE_CLONE_FAILED, SERVICE, e, "interrupted");
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.VOICE_CLONE_FAILED, SERVICE, e, e.getMessage());
        }
    }

    @Override
    public void deleteClone(String providerVoiceId) {
        HttpRequest request = HttpRequest.newBuilder(
                        voicesUri("/v1/voices/" + URLEncoder.encode(providerVoiceId, StandardCharsets.UTF_8)))
                .header("xi-api-key", findApiKey())
                .timeout(Duration.ofSeconds(15))
                .DELETE()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // 404: already gone at the vendor — what was asked for.
            if (response.statusCode() != 200 && response.statusCode() != 404) {
                throw new ExternalServiceException(ErrorCode.VOICE_CLONE_FAILED, SERVICE,
                        "HTTP " + response.statusCode() + " " + response.body());
            }
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.VOICE_CLONE_FAILED, SERVICE, e, "interrupted");
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.VOICE_CLONE_FAILED, SERVICE, e, e.getMessage());
        }
    }

    /** The configured TTS url names the host ({@code https://api.elevenlabs.io/v1/text-to-speech}). */
    private URI voicesUri(String path) {
        ElevenLabsTtsProperties settings = ttsProperties.elevenLabs();
        String url = settings != null && settings.url() != null ? settings.url() : "https://api.elevenlabs.io/v1/text-to-speech";
        return URI.create(url).resolve(path);
    }

    /** As ElevenLabsTtsProvider: the configured key, otherwise the ELEVENLABS_API_KEY environment variable. */
    private String findApiKey() {
        ElevenLabsTtsProperties settings = ttsProperties.elevenLabs();
        if (settings != null && settings.apiKey() != null && !settings.apiKey().isBlank()) {
            return settings.apiKey().trim();
        }
        String env = System.getenv("ELEVENLABS_API_KEY");
        return env != null ? env.trim() : "";
    }

    private static void writeField(ByteArrayOutputStream body, String boundary, String name, String value) {
        writeText(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n");
    }

    private static void writeText(ByteArrayOutputStream body, String text) {
        body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
