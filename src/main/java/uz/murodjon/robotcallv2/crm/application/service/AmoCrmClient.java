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
 * amoCRM & Kommo (Global amoCRM) REST API v4 Client.
 * Supports contact lookup, lead attachment, notes posting, call logging, and task scheduling.
 */
@Service
public class AmoCrmClient {

    private static final Logger log = LoggerFactory.getLogger(AmoCrmClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Look up contact and associated active leads by phone number.
     */
    public Optional<CrmClientSnapshot> findByPhone(String baseDomain, String accessToken, String phone) {
        if (baseDomain == null || accessToken == null || phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = "https://" + baseDomain + "/api/v4/contacts?query=" + URLEncoder.encode(phone.trim(), StandardCharsets.UTF_8) + "&with=leads";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("amoCRM contact search returned HTTP {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode contacts = root.path("_embedded").path("contacts");
            if (contacts.isMissingNode() || !contacts.isArray() || contacts.isEmpty()) {
                return Optional.empty();
            }

            JsonNode firstContact = contacts.get(0);
            long id = firstContact.path("id").asLong();
            String name = firstContact.path("name").asText("");

            return Optional.of(new CrmClientSnapshot(
                    name.isEmpty() ? phone : name,
                    null,
                    null,
                    null,
                    String.valueOf(id),
                    "uz-UZ"
            ));
        } catch (Exception e) {
            log.warn("amoCRM findByPhone failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Post a rich note to an amoCRM / Kommo lead.
     */
    public boolean addLeadNote(String baseDomain, String accessToken, long leadId, String noteText) {
        if (baseDomain == null || accessToken == null || leadId <= 0 || noteText == null || noteText.isBlank()) {
            return false;
        }
        try {
            String url = "https://" + baseDomain + "/api/v4/leads/" + leadId + "/notes";
            Map<String, Object> noteObj = Map.of(
                    "note_type", "common",
                    "params", Map.of("text", noteText)
            );
            String body = mapper.writeValueAsString(new Object[]{noteObj});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("amoCRM addLeadNote failed for lead {}: {}", leadId, e.getMessage());
            return false;
        }
    }

    /**
     * Log a telephony call event in amoCRM / Kommo.
     */
    public boolean logCall(String baseDomain, String accessToken, String phone, int durationSec,
                           String link, boolean successful, String callStatus) {
        if (baseDomain == null || accessToken == null) {
            return false;
        }
        try {
            String url = "https://" + baseDomain + "/api/v4/calls";
            Map<String, Object> callObj = new HashMap<>();
            callObj.put("direction", "outbound");
            callObj.put("uniq", "call-" + System.currentTimeMillis());
            callObj.put("duration", durationSec);
            callObj.put("source", "Uysot Voice AI");
            callObj.put("phone", phone);
            callObj.put("call_status", successful ? 4 : 2); // 4 = answered, 2 = no answer
            if (link != null && !link.isBlank()) {
                callObj.put("link", link);
            }
            String body = mapper.writeValueAsString(new Object[]{callObj});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("amoCRM logCall failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create a callback reminder task in amoCRM / Kommo.
     */
    public boolean createCallbackTask(String baseDomain, String accessToken, long entityId, String entityType,
                                      long completeTillEpochSec, String taskText) {
        if (baseDomain == null || accessToken == null) {
            return false;
        }
        try {
            String url = "https://" + baseDomain + "/api/v4/tasks";
            Map<String, Object> task = Map.of(
                    "text", taskText,
                    "complete_till", completeTillEpochSec,
                    "entity_id", entityId,
                    "entity_type", entityType != null ? entityType : "leads"
            );
            String body = mapper.writeValueAsString(new Object[]{task});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("amoCRM createCallbackTask failed: {}", e.getMessage());
            return false;
        }
    }
}
