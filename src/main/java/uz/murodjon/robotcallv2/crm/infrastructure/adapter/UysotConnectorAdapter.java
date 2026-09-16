package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.crm.application.port.output.CrmConnectorPort;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * {@link CrmConnectorPort} for the Uysot Open API v1 (PROJECT.md Stage 9, MASTER_ROADMAP.md §11).
 *
 * <p>Three things about this API shape the code below. Every response is an envelope —
 * {@code {data, message, error, accept, errors, requestId}} — so a payload is always read
 * out of {@code data}. Every <em>write</em> is asynchronous: it answers {@code 200} with a
 * {@code requestId} and nothing else, and {@code GET /v1/open-api/request/{requestId}}
 * then reports PENDING / SUCCESS / FAILED — the id of the row it created is never
 * exposed, which is why {@link #postNote} hands back the request id rather than a note id.
 * And the token travels in {@code X-Open-Api-Token}: the Open API ignores
 * {@code Authorization: Bearer}, which belongs to Uysot's own product API.
 *
 * <p>The address is the platform's ({@code voice-agent.crm.base-url}), not a tenant's, so
 * unlike the amoCRM and Bitrix24 connectors it is not screened as a tenant URL.
 */
@Component
public class UysotConnectorAdapter implements CrmConnectorPort {

    private static final Logger log = LoggerFactory.getLogger(UysotConnectorAdapter.class);

    /**
     * The two lookups run while a call is waiting on them: the dialer holds a concurrency
     * slot across {@link #findLead}, and an inbound caller is already on the line and
     * hearing silence across {@link #findByPhone}. Two seconds is generous for an internal
     * API; past that the call goes ahead with whatever facts the campaign imported.
     */
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Writes happen after the call has ended, so they may take longer — and whatever does
     * not land is picked up again by CallOutboxService.
     */
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Bounds connection setup for every call. Short because an unreachable CRM must not be
     * discovered slowly — nothing here is worth waiting on.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** Uysot matches a lead's contact phones on their last nine digits, digits only. */
    private static final int PHONE_SUFFIX_DIGITS = 9;

    private final CrmProperties crmProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public UysotConnectorAdapter(CrmProperties crmProperties) {
        this.crmProperties = crmProperties;
    }

    @Override
    public boolean isSupported(CrmProvider provider) {
        return provider == CrmProvider.UYSOT;
    }

    @Override
    public CrmClientSnapshot findLead(CrmConnection connection, long leadId) {
        if (crmProperties.leadPath() == null || crmProperties.leadPath().isBlank()) {
            return null;
        }
        try {
            String path = crmProperties.leadPath().replace("{id}", String.valueOf(leadId));
            HttpResponse<String> response = get(resolve(connection.baseUrl(), path), connection.accessToken(),
                    LOOKUP_TIMEOUT);
            if (response.statusCode() / 100 != 2) {
                log.warn("CRM lead lookup HTTP {} for lead {}", response.statusCode(), leadId);
                return null;
            }
            CrmClientSnapshot snapshot = CrmClientSnapshot.fromJson(mapper.readTree(response.body()));
            log.debug("CRM lead {} resolved: lang={} debt={}",
                    leadId, snapshot.preferredLanguage(), snapshot.debtAmount());
            return snapshot;
        } catch (Exception e) {
            log.warn("CRM lead lookup failed for {}: {}", leadId, e.getMessage());
            return null;
        }
    }

    /**
     * {@code POST /v1/open-api/lead/filter} answers with summary rows — no contacts and
     * no custom fields — which is all an inbound greeting needs: a name to say and the
     * lead's balance. The full record is a second hop through {@link #findLead} and is
     * not worth making the caller wait for.
     */
    @Override
    public CrmClientSnapshot findByPhone(CrmConnection connection, String phone) {
        if (crmProperties.leadFilterPath() == null || crmProperties.leadFilterPath().isBlank()) {
            return null;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("phone", phoneSuffix(phone));
            body.put("page", 1);
            body.put("size", 1);

            HttpResponse<String> response = post(resolve(connection.baseUrl(), crmProperties.leadFilterPath()),
                    connection.accessToken(), mapper.writeValueAsString(body), LOOKUP_TIMEOUT);
            if (response.statusCode() / 100 != 2) {
                log.warn("CRM phone lookup HTTP {} for {}", response.statusCode(), PhoneMasker.mask(phone));
                return null;
            }
            // data is a PageableData, so the rows are one level deeper than everywhere else.
            JsonNode rows = mapper.readTree(response.body()).path("data").path("data");
            if (!rows.isArray() || rows.isEmpty()) {
                log.debug("CRM phone lookup {} matched no lead", PhoneMasker.mask(phone));
                return null;
            }
            CrmClientSnapshot snapshot = CrmClientSnapshot.fromJson(rows.get(0));
            log.debug("CRM phone lookup {} resolved to a lead", PhoneMasker.mask(phone));
            return snapshot;
        } catch (Exception e) {
            log.warn("CRM phone lookup failed for {}: {}", PhoneMasker.mask(phone), e.getMessage());
            return null;
        }
    }

    /**
     * @return the async request id Uysot accepted the write under, or {@code null} when it
     *         did not. It is not the note's own id: this API never returns one.
     */
    @Override
    public String postNote(CrmConnection connection, long leadId, String noteText) {
        if (crmProperties.leadNotePath() == null || crmProperties.leadNotePath().isBlank()) {
            return null;
        }
        try {
            // The endpoint takes 1-10 notes at a time, so the body is an array even for one.
            ArrayNode body = mapper.createArrayNode();
            ObjectNode note = body.addObject();
            note.put("text", noteText);
            note.put("type", "PHONE");
            note.put("isPin", false);

            String path = crmProperties.leadNotePath().replace("{id}", String.valueOf(leadId));
            HttpResponse<String> response = post(resolve(connection.baseUrl(), path), connection.accessToken(),
                    mapper.writeValueAsString(body), WRITE_TIMEOUT);
            if (response.statusCode() / 100 != 2) {
                log.warn("CRM note HTTP {}: {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = mapper.readTree(response.body());
            String requestId = json.hasNonNull("requestId") ? json.get("requestId").asText() : null;
            if (requestId == null || requestId.isBlank()) {
                // A 2xx without a request id is this API telling us it did not enqueue the
                // write; treating it as posted would drop the note silently.
                log.warn("CRM note accepted for lead {} without a requestId: {}", leadId, response.body());
                return null;
            }
            return requestId;
        } catch (Exception e) {
            log.warn("CRM note post failed for lead {}: {}", leadId, e.getMessage());
            return null;
        }
    }

    /**
     * Skipped entirely until an employee id is configured: {@code employeeId} is required
     * by the endpoint and names a real Uysot employee, so there is nothing sensible to
     * invent.
     */
    @Override
    public boolean postCallHistory(CrmConnection connection, CrmCallHistoryEntry entry) {
        if (crmProperties.callHistoryPath() == null || crmProperties.callHistoryPath().isBlank()) {
            return false;
        }
        long leadId = entry.leadId();
        if (crmProperties.callHistoryEmployeeId() == null || crmProperties.callHistoryEmployeeId() <= 0) {
            log.debug("CRM call history skipped for lead {}: no voice-agent.crm.call-history-employee-id", leadId);
            return false;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("uuid", entry.callUuid());
            body.put("kind", "CALL");
            body.put("leadId", leadId);
            body.put("employeeId", crmProperties.callHistoryEmployeeId());
            body.put("startedAt", entry.startedAt().getEpochSecond());
            // The endpoint rejects a zero length; a call that never connected has no
            // conversation to record anyway.
            body.put("durationSec", Math.max(entry.durationSeconds(), 1));
            body.put("direction", entry.inbound() ? "INBOUND" : "OUTBOUND");
            body.put("answered", entry.answered());
            if (entry.clientPhone() != null && !entry.clientPhone().isBlank()) {
                body.put("clientPhone", entry.clientPhone());
            }
            if (entry.recordingUrl() != null && !entry.recordingUrl().isBlank()) {
                body.put("recordUrl", entry.recordingUrl());
            }

            HttpResponse<String> response = post(resolve(connection.baseUrl(), crmProperties.callHistoryPath()),
                    connection.accessToken(), mapper.writeValueAsString(body), WRITE_TIMEOUT);
            if (response.statusCode() / 100 != 2) {
                log.warn("CRM call-history HTTP {} for lead {}: {}", response.statusCode(), leadId, response.body());
                return false;
            }
            log.info("CRM call history enqueued for lead {} (uuid={})", leadId, entry.callUuid());
            return true;
        } catch (Exception e) {
            log.warn("CRM call-history post failed for lead {}: {}", leadId, e.getMessage());
            return false;
        }
    }

    /** Digits only, last nine — the shape {@code POST /lead/filter} matches phones on. */
    private static String phoneSuffix(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() > PHONE_SUFFIX_DIGITS
                ? digits.substring(digits.length() - PHONE_SUFFIX_DIGITS)
                : digits;
    }

    private static String resolve(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return base + (path.startsWith("/") ? path.substring(1) : path);
    }

    private HttpResponse<String> get(String url, String token, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET();
        return send(request, token);
    }

    private HttpResponse<String> post(String url, String token, String jsonBody, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        return send(request, token);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String token)
            throws IOException, InterruptedException {
        // X-Open-Api-Token only: the Open API ignores Authorization: Bearer, which is
        // Uysot's own product API's header.
        if (token != null) {
            request.header("X-Open-Api-Token", token);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
