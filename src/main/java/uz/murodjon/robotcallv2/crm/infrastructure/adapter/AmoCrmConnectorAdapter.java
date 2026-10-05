package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.crm.application.dto.CreateCrmTaskRequest;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateLeadStageRequest;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmConnectorPort;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@link CrmConnectorPort} for amoCRM and Kommo (the international amoCRM): REST API v4, every
 * request carrying the account's long-lived token as {@code Authorization: Bearer}.
 *
 * <p>The account address is the company's own, so it goes through {@link PublicUrlGuard}
 * before every request — a DNS answer can change after the address was saved — and only
 * its host is ever logged. Requests go through the shared {@link PublicHttpClient}: the host
 * is resolved only to public addresses at connect time and redirects are not followed, so
 * neither a changed DNS answer nor a 302 can carry a request past that check.
 */
@Component
public class AmoCrmConnectorAdapter implements CrmConnectorPort {

    private static final Logger log = LoggerFactory.getLogger(AmoCrmConnectorAdapter.class);

    /** As for Uysot: an inbound caller is hearing silence while the lookup runs. */
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(2);
    /** Writes happen after the call has ended; what does not land the outbox re-posts. */
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** amoCRM {@code call_status}: 4 — the conversation took place, 6 — did not get through. */
    private static final int CALL_STATUS_TALKED = 4;
    private static final int CALL_STATUS_NOT_REACHED = 6;
    /** How the call is labelled in amoCRM's call log. */
    private static final String CALL_SOURCE = "Uysot Voice AI";
    /** What a written note is referenced by when amoCRM's answer names no id. */
    private static final String ACCEPTED = "accepted";

    private final PublicHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AmoCrmConnectorAdapter(ObjectMapper objectMapper) {
        this(PublicHttpClient.create(CONNECT_TIMEOUT, WRITE_TIMEOUT), objectMapper);
    }

    AmoCrmConnectorAdapter(PublicHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isSupported(CrmProvider provider) {
        return provider == CrmProvider.AMOCRM || provider == CrmProvider.KOMMO;
    }

    /**
     * Not read: only the phone lookup exists for amoCRM, so a placed call goes ahead on the
     * facts its campaign imported.
     */
    @Override
    public CrmClientSnapshot findLead(CrmConnection connection, long leadId) {
        return null;
    }

    @Override
    public CrmClientSnapshot findByPhone(CrmConnection connection, String phone) {
        URI account = screen(connection);
        if (account == null) {
            return null;
        }
        try {
            String query = "?query=" + URLEncoder.encode(phone.trim(), StandardCharsets.UTF_8) + "&with=leads";
            PublicHttpResponse response = httpClient.send(
                    request("GET", account, "/api/v4/contacts" + query, connection, LOOKUP_TIMEOUT));
            if (response.statusCode() != 200) {
                // 204 is how amoCRM says nothing matched.
                log.debug("amoCRM contact search at {} returned HTTP {}", account.getHost(), response.statusCode());
                return null;
            }
            JsonNode contacts = objectMapper.readTree(response.readBodyText()).path("_embedded").path("contacts");
            String name = contacts.path(0).path("name").asText("").trim();
            // A name is all an inbound greeting needs; amoCRM keeps no balance to state.
            return name.isEmpty() ? null : new CrmClientSnapshot(name, null, null, null, null, null, null, null);
        } catch (Exception e) {
            log.warn("amoCRM contact search at {} failed for {}: {}",
                    account.getHost(), PhoneMasker.mask(phone), failure(e));
            return null;
        }
    }

    /** A common note on the lead — {@code campaign_target.client_id} carries its amoCRM id. */
    @Override
    public String postNote(CrmConnection connection, long leadId, String noteText) {
        URI account = screen(connection);
        if (account == null) {
            return null;
        }
        try {
            ArrayNode body = objectMapper.createArrayNode();
            ObjectNode note = body.addObject();
            note.put("note_type", "common");
            note.putObject("params").put("text", noteText);

            PublicHttpResponse response = post(account, "/api/v4/leads/" + leadId + "/notes", connection, body);
            if (response.statusCode() / 100 != 2) {
                log.warn("amoCRM note at {} HTTP {} for lead {}", account.getHost(), response.statusCode(), leadId);
                return null;
            }
            String noteId = objectMapper.readTree(response.readBodyText())
                    .path("_embedded").path("notes").path(0).path("id").asText("");
            return noteId.isBlank() ? ACCEPTED : noteId;
        } catch (Exception e) {
            log.warn("amoCRM note at {} failed for lead {}: {}", account.getHost(), leadId, failure(e));
            return null;
        }
    }

    /**
     * amoCRM files a call against whichever contact owns the number, so there is nothing to
     * send without one. {@code uniq} is the platform's call id, which makes a re-post the
     * same call rather than a second one.
     */
    @Override
    public boolean postCallHistory(CrmConnection connection, CrmCallHistoryEntry entry) {
        if (entry.clientPhone() == null || entry.clientPhone().isBlank()) {
            return false;
        }
        URI account = screen(connection);
        if (account == null) {
            return false;
        }
        try {
            ArrayNode body = objectMapper.createArrayNode();
            ObjectNode call = body.addObject();
            call.put("direction", entry.inbound() ? "inbound" : "outbound");
            call.put("uniq", entry.callUuid());
            call.put("duration", entry.durationSeconds());
            call.put("source", CALL_SOURCE);
            call.put("phone", entry.clientPhone());
            call.put("call_status", entry.answered() ? CALL_STATUS_TALKED : CALL_STATUS_NOT_REACHED);
            if (entry.recordingUrl() != null && !entry.recordingUrl().isBlank()) {
                call.put("link", entry.recordingUrl());
            }

            PublicHttpResponse response = post(account, "/api/v4/calls", connection, body);
            if (response.statusCode() / 100 != 2) {
                log.warn("amoCRM call log at {} HTTP {} for lead {}",
                        account.getHost(), response.statusCode(), entry.leadId());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("amoCRM call log at {} failed for lead {}: {}", account.getHost(), entry.leadId(), failure(e));
            return false;
        }
    }

    @Override
    public String createTask(CrmConnection connection, CreateCrmTaskRequest request) {
        URI account = screen(connection);
        if (account == null) {
            return null;
        }
        try {
            ArrayNode body = objectMapper.createArrayNode();
            ObjectNode task = body.addObject();
            task.put("entity_id", request.leadId());
            task.put("entity_type", "leads");
            task.put("text", request.title() + (request.description() != null ? " - " + request.description() : ""));
            if (request.dueAt() != null) {
                task.put("complete_till", request.dueAt().getEpochSecond());
            }
            if (request.responsibleId() != null && request.responsibleId() > 0) {
                task.put("responsible_user_id", request.responsibleId());
            }

            PublicHttpResponse response = post(account, "/api/v4/tasks", connection, body);
            if (response.statusCode() / 100 != 2) {
                log.warn("amoCRM task at {} HTTP {} for lead {}", account.getHost(), response.statusCode(), request.leadId());
                return null;
            }
            String taskId = objectMapper.readTree(response.readBodyText())
                    .path("_embedded").path("tasks").path(0).path("id").asText("");
            return taskId.isBlank() ? ACCEPTED : taskId;
        } catch (Exception e) {
            log.warn("amoCRM task at {} failed for lead {}: {}", account.getHost(), request.leadId(), failure(e));
            return null;
        }
    }

    @Override
    public boolean updateLeadStage(CrmConnection connection, UpdateLeadStageRequest request) {
        URI account = screen(connection);
        if (account == null) {
            return false;
        }
        try {
            ArrayNode body = objectMapper.createArrayNode();
            ObjectNode lead = body.addObject();
            lead.put("id", request.leadId());
            lead.put("status_id", request.statusId());

            PublicHttpRequest patchRequest = request("PATCH", account, "/api/v4/leads", connection, WRITE_TIMEOUT)
                    .addHeader("Content-Type", "application/json")
                    .setBody(objectMapper.writeValueAsString(body));
            PublicHttpResponse response = httpClient.send(patchRequest);
            return response.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("amoCRM update lead stage at {} failed for lead {}: {}", account.getHost(), request.leadId(), failure(e));
            return false;
        }
    }

    /**
     * The account address, when this server is still willing to call it; {@code null}
     * otherwise — PublicUrlGuard logs why, by host.
     */
    private static URI screen(CrmConnection connection) {
        if (connection.accessToken() == null || connection.accessToken().isBlank()) {
            return null;
        }
        return PublicUrlGuard.parsePublic(connection.baseUrl());
    }

    /** Scheme and host only: whatever page of the account was pasted, the API is at its root. */
    private static PublicHttpRequest request(String method, URI account, String pathAndQuery,
                                             CrmConnection connection, Duration timeout) {
        return PublicHttpRequest.create(method,
                        URI.create(account.getScheme() + "://" + account.getRawAuthority() + pathAndQuery))
                .setTimeout(timeout)
                .addHeader("Authorization", "Bearer " + connection.accessToken())
                .addHeader("Accept", "application/json");
    }

    private PublicHttpResponse post(URI account, String path, CrmConnection connection, Object body)
            throws IOException, InterruptedException {
        PublicHttpRequest request = request("POST", account, path, connection, WRITE_TIMEOUT)
                .addHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(body));
        return httpClient.send(request);
    }

    /** The failure's type, never its message — a message can quote the tenant's URL. */
    private static String failure(Exception e) {
        return e.getClass().getSimpleName();
    }
}
