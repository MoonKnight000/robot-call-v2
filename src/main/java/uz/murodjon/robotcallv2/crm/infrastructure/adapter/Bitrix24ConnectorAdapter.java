package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnectionCheck;
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
 * {@link CrmConnectorPort} for Bitrix24 through an inbound webhook,
 * {@code https://<portal>/rest/<user>/<code>/}, with REST method names appended to it. The
 * code in that path is the credential, so the URL is treated as one: stored encrypted and
 * never logged beyond its host. An OAuth access token, when there is one, travels as the
 * {@code auth} parameter.
 *
 * <p>The address is the company's own, so it goes through {@link PublicUrlGuard} before
 * every request, and requests go through the shared {@link PublicHttpClient}: the host is
 * resolved only to public addresses at connect time and redirects are not followed.
 */
@Component
public class Bitrix24ConnectorAdapter implements CrmConnectorPort {

    private static final Logger log = LoggerFactory.getLogger(Bitrix24ConnectorAdapter.class);

    /** As for Uysot: an inbound caller is hearing silence while the lookup runs. */
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(2);
    /** Writes happen after the call has ended; what does not land the outbox re-posts. */
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    /** A person pressing "check" is waiting on the answer, but not for long. */
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);

    /** {@code telephony.externalcall.register} TYPE: 1 — outgoing, 2 — incoming. */
    private static final int CALL_TYPE_OUTGOING = 1;
    private static final int CALL_TYPE_INCOMING = 2;
    /** {@code telephony.externalcall.finish} STATUS_CODE: 200 — answered, 304 — missed. */
    private static final int STATUS_ANSWERED = 200;
    private static final int STATUS_MISSED = 304;
    /** The CRM entity a note lands on — {@code campaign_target.client_id} carries a contact id. */
    private static final String NOTE_ENTITY_TYPE = "contact";
    /** What a written note is referenced by when Bitrix24's answer names no id. */
    private static final String ACCEPTED = "accepted";

    private final PublicHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public Bitrix24ConnectorAdapter(ObjectMapper objectMapper) {
        this(PublicHttpClient.create(CONNECT_TIMEOUT, WRITE_TIMEOUT), objectMapper);
    }

    Bitrix24ConnectorAdapter(PublicHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isSupported(CrmProvider provider) {
        return provider == CrmProvider.BITRIX24;
    }

    /**
     * Reads the webhook user's profile ({@code profile.json}), which needs no scope: a 200 with a
     * {@code result} means the webhook code is valid. A revoked webhook answers with an
     * {@code error} body, or 401/403.
     */
    @Override
    public CrmConnectionCheck checkConnection(CrmConnection connection) {
        URI webhook = PublicUrlGuard.parsePublic(connection.baseUrl());
        if (webhook == null) {
            return CrmConnectionCheck.refused("webhook address missing, or not public");
        }
        try {
            String auth = authQuery(connection);
            PublicHttpResponse response = httpClient.send(PublicHttpRequest.create("GET",
                            URI.create(method(webhook, "profile") + (auth.isEmpty() ? "" : "?" + auth.substring(1))))
                    .setTimeout(CHECK_TIMEOUT)
                    .addHeader("Accept", "application/json"));
            CrmConnectionCheck byStatus = CrmConnectionCheck.ofStatus(response.statusCode());
            if (!byStatus.ok()) {
                return byStatus;
            }
            return objectMapper.readTree(response.readBodyText()).has("result")
                    ? byStatus
                    : CrmConnectionCheck.refused("webhook rejected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CrmConnectionCheck.unreachable(e);
        } catch (IOException | RuntimeException e) {
            return CrmConnectionCheck.unreachable(e);
        }
    }

    /**
     * Not read: only the phone lookup exists for Bitrix24, so a placed call goes ahead on the
     * facts its campaign imported.
     */
    @Override
    public CrmClientSnapshot findLead(CrmConnection connection, long leadId) {
        return null;
    }

    @Override
    public CrmClientSnapshot findByPhone(CrmConnection connection, String phone) {
        URI webhook = PublicUrlGuard.parsePublic(connection.baseUrl());
        if (webhook == null) {
            return null;
        }
        try {
            String url = method(webhook, "crm.contact.list")
                    + "?FILTER%5BPHONE%5D=" + encode(phone.trim()) + authQuery(connection);
            PublicHttpResponse response = httpClient.send(PublicHttpRequest.create("GET", URI.create(url))
                    .setTimeout(LOOKUP_TIMEOUT)
                    .addHeader("Accept", "application/json"));
            if (response.statusCode() != 200) {
                log.debug("Bitrix24 contact lookup at {} returned HTTP {}", webhook.getHost(), response.statusCode());
                return null;
            }
            JsonNode contact = objectMapper.readTree(response.readBodyText()).path("result").path(0);
            String name = (contact.path("NAME").asText("") + " " + contact.path("LAST_NAME").asText("")).trim();
            // A name is all an inbound greeting needs; Bitrix24 keeps no balance to state.
            return name.isEmpty() ? null : new CrmClientSnapshot(name, null, null, null, null, null, null, null);
        } catch (Exception e) {
            log.warn("Bitrix24 contact lookup at {} failed for {}: {}",
                    webhook.getHost(), PhoneMasker.mask(phone), failure(e));
            return null;
        }
    }

    /** A comment on the contact's timeline ({@code crm.timeline.comment.add}). */
    @Override
    public String postNote(CrmConnection connection, long leadId, String noteText) {
        URI webhook = PublicUrlGuard.parsePublic(connection.baseUrl());
        if (webhook == null) {
            return null;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode fields = payload.putObject("fields");
        fields.put("ENTITY_ID", leadId);
        fields.put("ENTITY_TYPE", NOTE_ENTITY_TYPE);
        fields.put("COMMENT", noteText);
        addAuth(payload, connection);

        JsonNode reply = call(webhook, "crm.timeline.comment.add", payload);
        if (reply == null) {
            return null;
        }
        String commentId = reply.path("result").asText("");
        return commentId.isBlank() ? ACCEPTED : commentId;
    }

    /**
     * Registers the call and finishes it straight away — the conversation is already over.
     * Both methods act as a Bitrix24 user; an inbound webhook belongs to one, named in its
     * path, so that is the user the call is filed under.
     */
    @Override
    public boolean postCallHistory(CrmConnection connection, CrmCallHistoryEntry entry) {
        if (entry.clientPhone() == null || entry.clientPhone().isBlank()) {
            return false;
        }
        URI webhook = PublicUrlGuard.parsePublic(connection.baseUrl());
        if (webhook == null) {
            return false;
        }
        Integer userId = webhookUserId(webhook);

        ObjectNode register = objectMapper.createObjectNode();
        if (userId != null) {
            register.put("USER_ID", userId);
        }
        register.put("PHONE_NUMBER", entry.clientPhone());
        register.put("TYPE", entry.inbound() ? CALL_TYPE_INCOMING : CALL_TYPE_OUTGOING);
        register.put("CALL_START_DATE", entry.startedAt().toString());
        addAuth(register, connection);

        JsonNode registered = call(webhook, "telephony.externalcall.register", register);
        String callId = registered == null ? "" : registered.path("result").path("CALL_ID").asText("");
        if (callId.isBlank()) {
            return false;
        }

        ObjectNode finish = objectMapper.createObjectNode();
        finish.put("CALL_ID", callId);
        if (userId != null) {
            finish.put("USER_ID", userId);
        }
        finish.put("DURATION", entry.durationSeconds());
        finish.put("STATUS_CODE", entry.answered() ? STATUS_ANSWERED : STATUS_MISSED);
        if (entry.recordingUrl() != null && !entry.recordingUrl().isBlank()) {
            finish.put("RECORD_URL", entry.recordingUrl());
        }
        addAuth(finish, connection);
        return call(webhook, "telephony.externalcall.finish", finish) != null;
    }

    @Override
    public String createTask(CrmConnection connection, CreateCrmTaskRequest request) {
        URI webhook = PublicUrlGuard.parsePublic(connection.baseUrl());
        if (webhook == null) {
            return null;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode fields = payload.putObject("fields");
        fields.put("TITLE", request.title());
        if (request.description() != null) {
            fields.put("DESCRIPTION", request.description());
        }
        if (request.dueAt() != null) {
            fields.put("DEADLINE", request.dueAt().toString());
        }
        if (request.responsibleId() != null && request.responsibleId() > 0) {
            fields.put("RESPONSIBLE_ID", request.responsibleId());
        }
        addAuth(payload, connection);

        JsonNode reply = call(webhook, "tasks.task.add", payload);
        if (reply == null) {
            return null;
        }
        String taskId = reply.path("result").path("task").path("id").asText("");
        return taskId.isBlank() ? ACCEPTED : taskId;
    }

    @Override
    public boolean updateLeadStage(CrmConnection connection, UpdateLeadStageRequest request) {
        URI webhook = PublicUrlGuard.parsePublic(connection.baseUrl());
        if (webhook == null) {
            return false;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", request.leadId());
        ObjectNode fields = payload.putObject("fields");
        fields.put("STATUS_ID", String.valueOf(request.statusId()));
        addAuth(payload, connection);

        JsonNode reply = call(webhook, "crm.lead.update", payload);
        return reply != null && reply.path("result").asBoolean(false);
    }

    /** POSTs one REST method; the parsed answer, or {@code null} when Bitrix24 refused it. */
    private JsonNode call(URI webhook, String method, ObjectNode payload) {
        try {
            PublicHttpResponse response = httpClient.send(PublicHttpRequest.create("POST", URI.create(method(webhook, method)))
                    .setTimeout(WRITE_TIMEOUT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .setBody(objectMapper.writeValueAsString(payload)));
            if (response.statusCode() / 100 != 2) {
                log.warn("Bitrix24 {} at {} returned HTTP {}", method, webhook.getHost(), response.statusCode());
                return null;
            }
            JsonNode reply = objectMapper.readTree(response.readBodyText());
            if (reply.has("error")) {
                // The error code only: its description can echo the request back.
                log.warn("Bitrix24 {} at {} refused: {}", method, webhook.getHost(), reply.path("error").asText());
                return null;
            }
            return reply;
        } catch (Exception e) {
            log.warn("Bitrix24 {} at {} failed: {}", method, webhook.getHost(), failure(e));
            return null;
        }
    }

    private static String method(URI webhook, String name) {
        String base = webhook.toString();
        return (base.endsWith("/") ? base : base + "/") + name + ".json";
    }

    /** The user an inbound webhook acts as — the number after {@code /rest/} in its path. */
    private static Integer webhookUserId(URI webhook) {
        String[] segments = webhook.getPath() == null ? new String[0] : webhook.getPath().split("/");
        for (int i = 0; i + 1 < segments.length; i++) {
            if (segments[i].equals("rest") && segments[i + 1].matches("\\d{1,9}")) {
                return Integer.valueOf(segments[i + 1]);
            }
        }
        return null;
    }

    private static void addAuth(ObjectNode payload, CrmConnection connection) {
        if (connection.accessToken() != null && !connection.accessToken().isBlank()) {
            payload.put("auth", connection.accessToken());
        }
    }

    private static String authQuery(CrmConnection connection) {
        return connection.accessToken() != null && !connection.accessToken().isBlank()
                ? "&auth=" + encode(connection.accessToken())
                : "";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** The failure's type, never its message — a message can quote the webhook URL. */
    private static String failure(Exception e) {
        return e.getClass().getSimpleName();
    }
}
