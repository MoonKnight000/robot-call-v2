package uz.murodjon.robotcallv2.crm.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.integration.application.service.CrmIntegrationService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Uysot CRM API Client: Posts call notes, history and fetches client/debt data
 * (PROJECT.md Stage 9, MASTER_ROADMAP.md §11, Uysot Open API v1).
 */
@Component
public class CrmClient {

    private static final Logger log = LoggerFactory.getLogger(CrmClient.class);

    /**
     * Bounds connection setup for every call below. Short because an unreachable CRM must
     * not be discovered slowly — nothing here is worth waiting on.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * The two lookups run while a call is waiting on them: the dialer holds a concurrency
     * slot across {@link #fetchClient}, and an inbound caller is already on the line and
     * hearing silence across {@link #findByPhone}. Two seconds is generous for an internal
     * API; past that the call goes ahead with whatever facts the campaign imported.
     */
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Writes happen after the call has ended, so they may take longer — and whatever does
     * not land is picked up again by CallOutboxService.
     */
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);

    private final CrmProperties crmProperties;
    private final CrmIntegrationService integrations;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public CrmClient(CrmProperties crmProperties, CrmIntegrationService integrations) {
        this.crmProperties = crmProperties;
        this.integrations = integrations;
    }

    public boolean enabled() {
        return crmProperties.enabled() && crmProperties.baseUrl() != null && !crmProperties.baseUrl().isBlank();
    }

    public CrmClientSnapshot fetchClient(long companyId, Long clientId) {
        if (!enabled() || clientId == null || clientId == 0
                || crmProperties.clientPath() == null || crmProperties.clientPath().isBlank()) {
            return null;
        }
        try {
            String path = crmProperties.clientPath().replace("{id}", String.valueOf(clientId));
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(resolve(path)))
                    .timeout(LOOKUP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET();
            attachAuth(req, companyId);
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("CRM client lookup HTTP {} for client {}", resp.statusCode(), clientId);
                return null;
            }
            CrmClientSnapshot snapshot = CrmClientSnapshot.fromJson(mapper.readTree(resp.body()));
            log.debug("CRM client {} resolved: lang={} debt={}",
                    clientId, snapshot.preferredLanguage(), snapshot.debtAmount());
            return snapshot;
        } catch (Exception e) {
            log.warn("CRM client lookup failed for {}: {}", clientId, e.getMessage());
            return null;
        }
    }

    public CrmClientSnapshot findByPhone(long companyId, String phone) {
        if (!enabled() || phone == null || phone.isBlank()
                || crmProperties.clientByPhonePath() == null || crmProperties.clientByPhonePath().isBlank()) {
            return null;
        }
        try {
            String path = crmProperties.clientByPhonePath().replace("{phone}", phone);
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(resolve(path)))
                    .timeout(LOOKUP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET();
            attachAuth(req, companyId);
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("CRM phone lookup HTTP {} for {}", resp.statusCode(), phone);
                return null;
            }
            CrmClientSnapshot snapshot = CrmClientSnapshot.fromJson(mapper.readTree(resp.body()));
            log.debug("CRM phone lookup {} resolved: name={}", phone, snapshot.name());
            return snapshot;
        } catch (Exception e) {
            log.warn("CRM phone lookup failed for {}: {}", phone, e.getMessage());
            return null;
        }
    }

    public Long postNote(long companyId, long clientId, CallSummary summary) {
        if (!enabled() || summary == null) {
            return null;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("clientId", clientId);
            body.put("source", "voice-agent");
            String noteText = summary.summary() != null ? summary.summary() : "";
            body.put("note", noteText);
            body.put("content", noteText); // Support Uysot Open API lead-note field format
            if (summary.sentiment() != null) {
                body.put("sentiment", summary.sentiment().name());
            }
            if (summary.qaScore() != null) {
                body.put("qaScore", summary.qaScore());
            }
            if (summary.commitmentScore() != null) {
                body.put("commitmentScore", summary.commitmentScore());
            }
            if (summary.callbackAt() != null) {
                body.put("callbackAt", summary.callbackAt());
            }
            body.put("needsFollowUp", summary.needsFollowUp());

            Object reasonCode = summary.outcome().get("reasonCode");
            if (reasonCode != null) {
                body.put("reasonCode", reasonCode.toString());
            }
            Object promisedDate = summary.outcome().get("promisedDate");
            if (promisedDate != null) {
                body.put("promisedDate", promisedDate.toString());
            }
            Object promisedAmount = summary.outcome().get("promisedAmount");
            if (promisedAmount != null) {
                body.put("promisedAmount", promisedAmount.toString());
            }
            if (!summary.outcome().isEmpty()) {
                body.set("details", mapper.valueToTree(summary.outcome()));
            }

            String path = crmProperties.notePath() != null
                    ? crmProperties.notePath().replace("{id}", String.valueOf(clientId))
                    : "v1/open-api/lead/" + clientId + "/note";

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(resolve(path)))
                    .timeout(WRITE_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            attachAuth(req, companyId);

            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("CRM note HTTP {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonNode json = mapper.readTree(resp.body());
            Long id = json.hasNonNull("id") ? json.get("id").asLong() : null;
            log.info("CRM note created for client {} (noteId={}, qaScore={}, sentiment={})",
                    clientId, id, summary.qaScore(), summary.sentiment());
            return id;
        } catch (Exception e) {
            log.warn("CRM note post failed for client {}: {}", clientId, e.getMessage());
            return null;
        }
    }

    public boolean postCallHistory(long companyId, String phone, String callDirection,
                                   int durationSeconds, String disposition, String recordingUrl) {
        if (!enabled() || crmProperties.callHistoryPath() == null || crmProperties.callHistoryPath().isBlank()) {
            return false;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("phone", phone);
            body.put("direction", callDirection != null ? callDirection : "OUTBOUND");
            body.put("duration", durationSeconds);
            body.put("status", disposition);
            if (recordingUrl != null && !recordingUrl.isBlank()) {
                body.put("recordUrl", recordingUrl);
            }
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(resolve(crmProperties.callHistoryPath())))
                    .timeout(WRITE_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            attachAuth(req, companyId);

            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("CRM call-history post failed for {}: {}", phone, e.getMessage());
            return false;
        }
    }

    private void attachAuth(HttpRequest.Builder req, long companyId) {
        authToken(companyId).ifPresent(token -> {
            req.header("X-Open-Api-Token", token);
            req.header("Authorization", "Bearer " + token);
        });
    }

    private String resolve(String path) {
        String base = crmProperties.baseUrl().endsWith("/") ? crmProperties.baseUrl() : crmProperties.baseUrl() + "/";
        return base + (path.startsWith("/") ? path.substring(1) : path);
    }

    private Optional<String> authToken(long companyId) {
        Optional<String> connected = integrations.currentAccessToken(companyId);
        if (connected.isPresent()) {
            return connected;
        }
        return crmProperties.apiToken() != null && !crmProperties.apiToken().isBlank()
                ? Optional.of(crmProperties.apiToken())
                : Optional.empty();
    }
}
