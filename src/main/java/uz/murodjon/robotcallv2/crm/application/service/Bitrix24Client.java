package uz.murodjon.robotcallv2.crm.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bitrix24 REST API & Telephony Connector.
 * Supports contact lookup, timeline comment injection, external call logging, and CRM activity creation.
 */
@Service
public class Bitrix24Client {

    private static final Logger log = LoggerFactory.getLogger(Bitrix24Client.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Find contact by phone in Bitrix24 CRM via crm.contact.list REST endpoint.
     */
    public Optional<CrmClientSnapshot> findContactByPhone(String webhookOrDomain, String accessToken, String phone) {
        if (webhookOrDomain == null || phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        try {
            String baseUrl = sanitizeBaseUrl(webhookOrDomain);
            String url = baseUrl + "/crm.contact.list.json?FILTER[PHONE]=" + URLEncoder.encode(phone.trim(), StandardCharsets.UTF_8);
            if (accessToken != null && !accessToken.isBlank()) {
                url += "&auth=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("Bitrix24 contact lookup HTTP {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode result = root.path("result");
            if (result.isMissingNode() || !result.isArray() || result.isEmpty()) {
                return Optional.empty();
            }

            JsonNode contact = result.get(0);
            long id = contact.path("ID").asLong();
            String name = contact.path("NAME").asText("") + " " + contact.path("LAST_NAME").asText("");

            return Optional.of(new CrmClientSnapshot(
                    name.trim().isEmpty() ? phone : name.trim(),
                    null,
                    null,
                    null,
                    String.valueOf(id),
                    "uz-UZ"
            ));
        } catch (Exception e) {
            log.warn("Bitrix24 findContactByPhone failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Add comment to CRM timeline (crm.timeline.comment.add).
     */
    public boolean addTimelineComment(String webhookOrDomain, String accessToken, long entityId, String entityType, String comment) {
        if (webhookOrDomain == null || entityId <= 0 || comment == null || comment.isBlank()) {
            return false;
        }
        try {
            String baseUrl = sanitizeBaseUrl(webhookOrDomain);
            String url = baseUrl + "/crm.timeline.comment.add.json";

            Map<String, Object> fields = Map.of(
                    "ENTITY_ID", entityId,
                    "ENTITY_TYPE", entityType != null ? entityType : "contact",
                    "COMMENT", comment
            );
            Map<String, Object> payload = new HashMap<>();
            payload.put("fields", fields);
            if (accessToken != null && !accessToken.isBlank()) {
                payload.put("auth", accessToken);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("Bitrix24 addTimelineComment failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Register and finish external telephony call in Bitrix24 telephony log.
     */
    public boolean logTelephonyCall(String webhookOrDomain, String accessToken, String phone, int durationSec,
                                    String recordingUrl, boolean successful) {
        if (webhookOrDomain == null || phone == null) {
            return false;
        }
        try {
            String baseUrl = sanitizeBaseUrl(webhookOrDomain);
            String registerUrl = baseUrl + "/telephony.externalcall.register.json";

            Map<String, Object> regFields = new HashMap<>();
            regFields.put("PHONE_NUMBER", phone);
            regFields.put("TYPE", 1); // 1 = Outbound call
            regFields.put("CALL_START_DATE", java.time.Instant.now().toString());
            if (accessToken != null && !accessToken.isBlank()) {
                regFields.put("auth", accessToken);
            }

            HttpRequest regReq = HttpRequest.newBuilder()
                    .uri(URI.create(registerUrl))
                    .timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(regFields)))
                    .build();

            HttpResponse<String> regResp = http.send(regReq, HttpResponse.BodyHandlers.ofString());
            if (regResp.statusCode() != 200) {
                return false;
            }

            JsonNode regJson = mapper.readTree(regResp.body());
            String callId = regJson.path("result").path("CALL_ID").asText();
            if (callId == null || callId.isBlank()) {
                return false;
            }

            // Finish external call
            String finishUrl = baseUrl + "/telephony.externalcall.finish.json";
            Map<String, Object> finFields = new HashMap<>();
            finFields.put("CALL_ID", callId);
            finFields.put("DURATION", durationSec);
            finFields.put("STATUS_CODE", successful ? 200 : 304);
            if (recordingUrl != null && !recordingUrl.isBlank()) {
                finFields.put("RECORD_URL", recordingUrl);
            }
            if (accessToken != null && !accessToken.isBlank()) {
                finFields.put("auth", accessToken);
            }

            HttpRequest finReq = HttpRequest.newBuilder()
                    .uri(URI.create(finishUrl))
                    .timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(finFields)))
                    .build();

            HttpResponse<String> finResp = http.send(finReq, HttpResponse.BodyHandlers.ofString());
            return finResp.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("Bitrix24 logTelephonyCall failed: {}", e.getMessage());
            return false;
        }
    }

    private String sanitizeBaseUrl(String domainOrWebhook) {
        String trimmed = domainOrWebhook.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }
}
