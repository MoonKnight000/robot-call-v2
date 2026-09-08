package uz.murodjon.robotcallv2.crm.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.time.Instant;
import java.util.Optional;

/**
 * Uysot Open API v1 client: reads the lead a call is about and writes back what the call
 * produced (PROJECT.md Stage 9, MASTER_ROADMAP.md §11).
 *
 * <p>Three things about this API shape the code below. Every response is an envelope —
 * {@code {data, message, error, accept, errors, requestId}} — so a payload is always read
 * out of {@code data}. Every <em>write</em> is asynchronous: it answers {@code 200} with a
 * {@code requestId} and nothing else, and {@code GET /v1/open-api/request/{requestId}}
 * then reports PENDING / SUCCESS / FAILED — the id of the row it created is never
 * exposed, which is why {@link #postNote} hands back the request id rather than a note id.
 * And the token travels in {@code X-Open-Api-Token}: the Open API ignores
 * {@code Authorization: Bearer}, which belongs to Uysot's own product API.
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

    /**
     * How much of the composed note is sent. The endpoint documents no limit, and a note
     * is read by a person: everything past a couple of screens is the summary having gone
     * wrong, not detail worth storing.
     */
    private static final int MAX_NOTE_CHARS = 4000;

    /** Uysot matches a lead's contact phones on their last nine digits, digits only. */
    private static final int PHONE_SUFFIX_DIGITS = 9;

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

    /**
     * The lead a placed call is about, by its Uysot id — {@code campaign_target.client_id}
     * carries that id for every target the debtor import queued.
     */
    public CrmClientSnapshot fetchClient(long companyId, Long leadId) {
        if (!enabled() || leadId == null || leadId == 0
                || crmProperties.leadPath() == null || crmProperties.leadPath().isBlank()) {
            return null;
        }
        try {
            String path = crmProperties.leadPath().replace("{id}", String.valueOf(leadId));
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(resolve(path)))
                    .timeout(LOOKUP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET();
            attachAuth(request, companyId);
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
     * Who is calling, for an inbound call that arrived with nothing but a number.
     *
     * <p>{@code POST /v1/open-api/lead/filter} answers with summary rows — no contacts and
     * no custom fields — which is all an inbound greeting needs: a name to say and the
     * lead's balance. The full record is a second hop through {@link #fetchClient} and is
     * not worth making the caller wait for.
     */
    public CrmClientSnapshot findByPhone(long companyId, String phone) {
        if (!enabled() || phone == null || phone.isBlank()
                || crmProperties.leadFilterPath() == null || crmProperties.leadFilterPath().isBlank()) {
            return null;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("phone", phoneSuffix(phone));
            body.put("page", 1);
            body.put("size", 1);

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(resolve(crmProperties.leadFilterPath())))
                    .timeout(LOOKUP_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            attachAuth(request, companyId);

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("CRM phone lookup HTTP {} for {}", response.statusCode(), phone);
                return null;
            }
            // data is a PageableData, so the rows are one level deeper than everywhere else.
            JsonNode rows = mapper.readTree(response.body()).path("data").path("data");
            if (!rows.isArray() || rows.isEmpty()) {
                log.debug("CRM phone lookup {} matched no lead", phone);
                return null;
            }
            CrmClientSnapshot snapshot = CrmClientSnapshot.fromJson(rows.get(0));
            log.debug("CRM phone lookup {} resolved: name={}", phone, snapshot.name());
            return snapshot;
        } catch (Exception e) {
            log.warn("CRM phone lookup failed for {}: {}", phone, e.getMessage());
            return null;
        }
    }

    /**
     * Writes the call's summary onto the lead as a note.
     *
     * @return the async request id Uysot accepted the write under, or {@code null} when it
     *         did not — the outbox re-posts on {@code null} and stops on anything else.
     *         It is not the note's own id: this API never returns one.
     */
    public String postNote(long companyId, long leadId, CallSummary summary) {
        if (!enabled() || summary == null || leadId == 0
                || crmProperties.leadNotePath() == null || crmProperties.leadNotePath().isBlank()) {
            return null;
        }
        try {
            // The endpoint takes 1-10 notes at a time, so the body is an array even for one.
            ArrayNode body = mapper.createArrayNode();
            ObjectNode note = body.addObject();
            note.put("text", noteText(summary));
            note.put("type", "PHONE");
            note.put("isPin", false);

            String path = crmProperties.leadNotePath().replace("{id}", String.valueOf(leadId));
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(resolve(path)))
                    .timeout(WRITE_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            attachAuth(request, companyId);

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
            log.info("CRM note enqueued for lead {} (requestId={}, qaScore={}, sentiment={})",
                    leadId, requestId, summary.qaScore(), summary.sentiment());
            return requestId;
        } catch (Exception e) {
            log.warn("CRM note post failed for lead {}: {}", leadId, e.getMessage());
            return null;
        }
    }

    /**
     * Records the conversation itself on the lead's call history.
     *
     * <p>Separate from the note because Uysot models the two separately, and skipped
     * entirely until an employee id is configured: {@code employeeId} is required by the
     * endpoint and names a real Uysot employee, so there is nothing sensible to invent.
     *
     * @param callUuid this platform's own id for the conversation — the idempotency key,
     *                 so a re-post after a failed write does not create a second record
     * @return whether Uysot accepted the write
     */
    public boolean postCallHistory(long companyId, long leadId, String callUuid, Instant startedAt,
                                   int durationSeconds, boolean inbound, boolean answered,
                                   String clientPhone, String recordingUrl) {
        if (!enabled() || leadId == 0 || callUuid == null || startedAt == null
                || crmProperties.callHistoryPath() == null || crmProperties.callHistoryPath().isBlank()) {
            return false;
        }
        if (crmProperties.callHistoryEmployeeId() == null || crmProperties.callHistoryEmployeeId() <= 0) {
            log.debug("CRM call history skipped for lead {}: no voice-agent.crm.call-history-employee-id", leadId);
            return false;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("uuid", callUuid);
            body.put("kind", "CALL");
            body.put("leadId", leadId);
            body.put("employeeId", crmProperties.callHistoryEmployeeId());
            body.put("startedAt", startedAt.getEpochSecond());
            // The endpoint rejects a zero length; a call that never connected has no
            // conversation to record anyway.
            body.put("durationSec", Math.max(durationSeconds, 1));
            body.put("direction", inbound ? "INBOUND" : "OUTBOUND");
            body.put("answered", answered);
            if (clientPhone != null && !clientPhone.isBlank()) {
                body.put("clientPhone", clientPhone);
            }
            if (recordingUrl != null && !recordingUrl.isBlank()) {
                body.put("recordUrl", recordingUrl);
            }

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(resolve(crmProperties.callHistoryPath())))
                    .timeout(WRITE_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            attachAuth(request, companyId);

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("CRM call-history HTTP {} for lead {}: {}", response.statusCode(), leadId, response.body());
                return false;
            }
            log.info("CRM call history enqueued for lead {} (uuid={})", leadId, callUuid);
            return true;
        } catch (Exception e) {
            log.warn("CRM call-history post failed for lead {}: {}", leadId, e.getMessage());
            return false;
        }
    }

    /**
     * The note as a person reads it in Uysot. One free-text field is all the endpoint
     * takes, so the structured half of the summary — the promise, the sentiment, the
     * callback — is spelled out in the text rather than dropped.
     */
    private String noteText(CallSummary summary) {
        StringBuilder text = new StringBuilder();
        if (summary.summary() != null && !summary.summary().isBlank()) {
            text.append(summary.summary().trim());
        }
        appendOutcome(text, "Sabab", summary.outcome().get("reasonCode"));
        appendOutcome(text, "Va'da qilingan summa", summary.outcome().get("promisedAmount"));
        appendOutcome(text, "Va'da qilingan sana", summary.outcome().get("promisedDate"));
        if (summary.sentiment() != null) {
            append(text, "Kayfiyat", summary.sentiment().name());
        }
        if (summary.callbackAt() != null && !summary.callbackAt().isBlank()) {
            append(text, "Qayta qo'ng'iroq", summary.callbackAt());
        }
        if (summary.needsFollowUp()) {
            append(text, "Kuzatuv kerak", summary.followUpNote() != null ? summary.followUpNote() : "ha");
        }
        if (summary.qaScore() != null) {
            append(text, "Suhbat sifati", summary.qaScore() + "/100");
        }
        String note = text.toString().trim();
        if (note.isEmpty()) {
            note = "AI operator bilan suhbat bo'lib o'tdi.";
        }
        return note.length() > MAX_NOTE_CHARS ? note.substring(0, MAX_NOTE_CHARS) : note;
    }

    private static void appendOutcome(StringBuilder text, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            append(text, label, value.toString());
        }
    }

    private static void append(StringBuilder text, String label, String value) {
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(label).append(": ").append(value);
    }

    /** Digits only, last nine — the shape {@code POST /lead/filter} matches phones on. */
    private static String phoneSuffix(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() > PHONE_SUFFIX_DIGITS
                ? digits.substring(digits.length() - PHONE_SUFFIX_DIGITS)
                : digits;
    }

    private void attachAuth(HttpRequest.Builder request, long companyId) {
        // X-Open-Api-Token only: the Open API ignores Authorization: Bearer, which is
        // Uysot's own product API's header.
        authToken(companyId).ifPresent(token -> request.header("X-Open-Api-Token", token));
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
