package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactSourcePort;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactWriterPort;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactPage;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactRecord;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmFieldOption;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uysot leads as contacts, through the Open API (apidoc.app.uysot.uz):
 * {@code POST /v1/open-api/lead/filter} by {@code updatedTimestampFrom}, oldest change first,
 * then {@code GET /v1/open-api/lead/{id}} for each lead — the list rows carry no phones.
 * One lead is one record: its first contact's name, every contact's phones, and the values of
 * its lead and contact custom fields by field id ({@code GET /v1/open-api/crm-field}). The write-back
 * is {@code POST /v1/open-api/lead/{id}/field/list}; it reaches the lead's own fields only.
 *
 * <p>The Open API allows 60 requests a minute per token, and the same token serves the live
 * calls' CRM lookups, so this adapter keeps to {@link #REQUEST_SPACING} between requests —
 * half the allowance.
 */
@Component
public class UysotContactSyncAdapter implements CrmContactSourcePort, CrmContactWriterPort {

    private static final String SERVICE = "uysot";
    private static final String LEAD_FILTER_PATH = "/v1/open-api/lead/filter";
    private static final String LEAD_PATH = "/v1/open-api/lead/";
    private static final String CRM_FIELD_PATH = "/v1/open-api/crm-field";
    private static final int PAGE_SIZE = 20;
    private static final Duration REQUEST_SPACING = Duration.ofSeconds(2);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    /** When the next request may leave, as {@link System#nanoTime()}. */
    private long nextRequestAt = System.nanoTime();

    public UysotContactSyncAdapter(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isSupported(CrmProvider provider) {
        return provider == CrmProvider.UYSOT;
    }

    @Override
    public CrmContactPage findContactsModifiedSince(CrmConnection connection, Instant since, String pageToken,
                                                    Set<String> fields) {
        int page = pageToken == null ? 1 : Integer.parseInt(pageToken);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", page);
        body.put("size", PAGE_SIZE);
        // ID ascending as well: Uysot otherwise breaks ties by ID DESC, and the pages must stay in one order.
        body.putObject("orders").put("UPDATED_TIMESTAMP", "ASC").put("ID", "ASC");
        if (since != null) {
            body.put("updatedTimestampFrom", since.getEpochSecond());
        }
        JsonNode pageData = send(connection, "POST", LEAD_FILTER_PATH, body, ErrorCode.CRM_CONTACT_SYNC_FAILED).path("data");
        List<CrmContactRecord> records = new ArrayList<>();
        for (JsonNode row : pageData.path("data")) {
            long leadId = row.path("id").asLong(0);
            if (leadId <= 0) {
                continue;
            }
            JsonNode lead = send(connection, "GET", LEAD_PATH + leadId, null, ErrorCode.CRM_CONTACT_SYNC_FAILED).path("data");
            records.add(toRecord(leadId, lead.isObject() ? lead : row, row, fields));
        }
        int currentPage = pageData.path("currentPage").asInt(page);
        int totalPages = pageData.path("totalPages").asInt(0);
        return new CrmContactPage(records, currentPage < totalPages ? String.valueOf(currentPage + 1) : null);
    }

    @Override
    public List<CrmFieldOption> findContactFields(CrmConnection connection) {
        JsonNode reply = send(connection, "GET", CRM_FIELD_PATH, null, ErrorCode.CRM_CONTACT_SYNC_FAILED);
        JsonNode items = reply.path("data").isArray() ? reply.path("data") : reply;
        List<CrmFieldOption> options = new ArrayList<>();
        for (JsonNode field : items) {
            String name = field.path("name").asText("").trim();
            String label = "CONTACT".equals(field.path("infoType").asText()) ? name + " (kontakt)" : name;
            options.add(new CrmFieldOption(field.path("id").asText(), label, field.path("type").asText(null)));
        }
        return options;
    }

    /**
     * {@code POST /v1/open-api/lead/{id}/field/list} with {@code [{id, value}]}. Uysot applies it
     * asynchronously; a 2xx means it was accepted. A contact-level field ({@code infoType CONTACT})
     * is not reachable through this call — Uysot refuses it and the write is tried again later.
     */
    @Override
    public void updateContactFields(CrmConnection connection, String externalId, Map<String, String> values) {
        long leadId;
        try {
            leadId = Long.parseLong(externalId);
        } catch (NumberFormatException e) {
            throw new ExternalServiceException(ErrorCode.CRM_CONTACT_PUSH_FAILED, SERVICE, "lead id is not numeric");
        }
        ArrayNode body = objectMapper.createArrayNode();
        values.forEach((field, value) -> {
            ObjectNode item = body.addObject();
            try {
                item.put("id", Long.parseLong(field));
            } catch (NumberFormatException e) {
                item.put("id", field);
            }
            item.put("value", value);
        });
        send(connection, "POST", LEAD_PATH + leadId + "/field/list", body, ErrorCode.CRM_CONTACT_PUSH_FAILED);
    }

    static CrmContactRecord toRecord(long leadId, JsonNode lead, JsonNode row, Set<String> fields) {
        List<String> phones = new ArrayList<>();
        String contactName = null;
        Map<String, String> values = new LinkedHashMap<>();
        collectFields(lead.path("fields"), fields, values);
        for (JsonNode contact : lead.path("contacts")) {
            if (contactName == null && !contact.path("name").asText("").isBlank()) {
                contactName = contact.path("name").asText().trim();
            }
            for (JsonNode phone : contact.path("phones")) {
                String value = phone.asText("").trim();
                if (!value.isEmpty()) {
                    phones.add(value);
                }
            }
            collectFields(contact.path("fields"), fields, values);
        }
        String name = contactName != null ? contactName : lead.path("name").asText(row.path("name").asText("")).trim();
        long updated = lead.path("updatedTimestamp").asLong(row.path("updatedTimestamp").asLong(0));
        return new CrmContactRecord(String.valueOf(leadId), name, phones, values,
                updated > 0 ? Instant.ofEpochSecond(updated) : null);
    }

    /** The requested fields' values by field id; the lead's own values come first and are not overwritten. */
    private static void collectFields(JsonNode items, Set<String> fields, Map<String, String> values) {
        for (JsonNode item : items) {
            String id = item.path("id").asText();
            if (!fields.contains(id) || values.containsKey(id)) {
                continue;
            }
            String text = text(item);
            if (text != null) {
                values.put(id, text);
            }
        }
    }

    /** A field value as text: a multi-select's list joined by commas, a number field's number, else its value. */
    private static String text(JsonNode item) {
        JsonNode list = item.path("valueList");
        if (list.isArray() && !list.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode part : list) {
                String value = part.isObject() ? part.path("name").asText(part.path("value").asText("")) : part.asText("");
                if (!value.isBlank()) {
                    parts.add(value.trim());
                }
            }
            return parts.isEmpty() ? null : String.join(", ", parts);
        }
        JsonNode number = item.path("number");
        if (number.isNumber()) {
            return number.asText();
        }
        String value = item.path("value").asText("").trim();
        return value.isEmpty() ? null : value;
    }

    /** One Open API request, spaced out from the last one; the parsed answer or an {@link ExternalServiceException}. */
    private JsonNode send(CrmConnection connection, String method, String path, JsonNode body, ErrorCode errorCode) {
        if (connection.baseUrl() == null || connection.baseUrl().isBlank()) {
            throw new ExternalServiceException(errorCode, SERVICE, "Open API address is not configured");
        }
        try {
            waitForTurn();
            String base = connection.baseUrl().endsWith("/") ? connection.baseUrl() : connection.baseUrl() + "/";
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(base + path.substring(1)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json");
            if (connection.accessToken() != null) {
                request.header("X-Open-Api-Token", connection.accessToken());
            }
            if (body != null) {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ExternalServiceException(errorCode, SERVICE,
                        method + " " + path + " answered HTTP " + response.statusCode()
                                + (response.statusCode() == 429 ? " (rate limit)" : ""));
            }
            return objectMapper.readTree(response.body());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(errorCode, SERVICE, e, "interrupted");
        } catch (Exception e) {
            throw new ExternalServiceException(errorCode, SERVICE, e,
                    method + " " + path + " failed: " + e.getClass().getSimpleName());
        }
    }

    /** Sleeps until {@link #REQUEST_SPACING} has passed since the previous request. */
    private synchronized void waitForTurn() throws InterruptedException {
        long now = System.nanoTime();
        long wait = nextRequestAt - now;
        if (wait > 0) {
            Thread.sleep(Duration.ofNanos(wait));
        }
        nextRequestAt = Math.max(now, nextRequestAt) + REQUEST_SPACING.toNanos();
    }
}
