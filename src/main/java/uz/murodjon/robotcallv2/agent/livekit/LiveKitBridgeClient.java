package uz.murodjon.robotcallv2.agent.livekit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.rtp.RtpCodec;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * The Go media bridge (livekit-bridge/): what Asterisk's externalMedia is to an Asterisk call.
 * It joins a call's room, sends the phone leg's audio to our RTP port as G.711 and publishes
 * what we send back as the agent's track. Only reachable inside the compose network.
 */
@Component
public class LiveKitBridgeClient {

    private static final String SERVICE = "livekit-bridge";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final LiveKitProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public LiveKitBridgeClient(LiveKitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Puts the bridge in {@code roomName}, streaming to {@code rtpHost:rtpPort}.
     *
     * @return the bridge's own RTP address — where our endpoint sends the agent's voice
     */
    public InetSocketAddress startMedia(String roomName, String rtpHost, int rtpPort, RtpCodec codec) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of(
                    "room", roomName, "rtpHost", rtpHost, "rtpPort", rtpPort, "codec", codec.name()));
        } catch (IOException e) {
            throw new IllegalStateException("could not serialise a bridge request", e);
        }
        HttpResponse<String> response = send("join", roomName, HttpRequest.newBuilder(uri("/calls"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
        try {
            JsonNode json = objectMapper.readTree(response.body());
            return new InetSocketAddress(json.path("rtpHost").asText(), json.path("rtpPort").asInt());
        } catch (IOException | IllegalArgumentException e) {
            throw new ExternalServiceException(ErrorCode.LIVEKIT_BRIDGE_FAILED, SERVICE, e, "join", roomName, e.getMessage());
        }
    }

    /** Takes the bridge out of the room. A room it already left is not an error. */
    public void stopMedia(String roomName) {
        send("leave", roomName, HttpRequest.newBuilder(
                uri("/calls/" + URLEncoder.encode(roomName, StandardCharsets.UTF_8))).DELETE());
    }

    /**
     * Presses {@code digits} ({@code 0-9 * #}) on the phone leg, one after another; returns once
     * the last is sent. A room the bridge is not in is not an error.
     */
    public void sendDtmf(String roomName, String digits) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of("digits", digits));
        } catch (IOException e) {
            throw new IllegalStateException("could not serialise a bridge request", e);
        }
        send("dtmf", roomName, HttpRequest.newBuilder(
                        uri("/calls/" + URLEncoder.encode(roomName, StandardCharsets.UTF_8) + "/dtmf"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    private HttpResponse<String> send(String action, String roomName, HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = httpClient.send(request.timeout(TIMEOUT).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 && response.statusCode() != 404) {
                throw new ExternalServiceException(ErrorCode.LIVEKIT_BRIDGE_FAILED, SERVICE, action, roomName,
                        "HTTP " + response.statusCode() + " " + response.body());
            }
            return response;
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.LIVEKIT_BRIDGE_FAILED, SERVICE, e, action, roomName, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.LIVEKIT_BRIDGE_FAILED, SERVICE, e, action, roomName, "interrupted");
        }
    }

    private URI uri(String path) {
        String base = properties.bridgeUrl();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
    }
}
