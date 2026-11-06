package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
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
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bitrix24 contacts through the company's inbound webhook: {@code crm.contact.list} ordered by
 * {@code DATE_MODIFY}, 50 to a page, {@code crm.contact.fields} for the mapping screen, and
 * {@code crm.contact.update} for the write-back.
 */
@Component
public class Bitrix24ContactSyncAdapter implements CrmContactSourcePort, CrmContactWriterPort {

    private static final String SERVICE = "bitrix24";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration PAGE_TIMEOUT = Duration.ofSeconds(15);
    /** Bitrix24 multi-fields: a value is written as one more entry of the list. */
    private static final Set<String> MULTI_FIELDS = Set.of("EMAIL", "WEB", "IM");
    private static final List<String> BASE_SELECT = List.of("ID", "NAME", "LAST_NAME", "PHONE", "DATE_MODIFY");

    private final PublicHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public Bitrix24ContactSyncAdapter(ObjectMapper objectMapper) {
        this(PublicHttpClient.create(CONNECT_TIMEOUT, PAGE_TIMEOUT), objectMapper);
    }

    Bitrix24ContactSyncAdapter(PublicHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isSupported(CrmProvider provider) {
        return provider == CrmProvider.BITRIX24;
    }

    @Override
    public CrmContactPage findContactsModifiedSince(CrmConnection connection, Instant since, String pageToken,
                                                    Set<String> fields) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode filter = payload.putObject("filter");
        if (since != null) {
            filter.put(">=DATE_MODIFY", since.toString());
        }
        payload.putObject("order").put("DATE_MODIFY", "ASC").put("ID", "ASC");
        ArrayNode select = payload.putArray("select");
        Set<String> selected = new LinkedHashSet<>(BASE_SELECT);
        selected.addAll(fields);
        selected.forEach(select::add);
        if (pageToken != null) {
            payload.put("start", pageToken);
        }
        JsonNode reply = call(connection, "crm.contact.list", payload, ErrorCode.CRM_CONTACT_SYNC_FAILED);
        List<CrmContactRecord> records = new ArrayList<>();
        for (JsonNode row : reply.path("result")) {
            records.add(toRecord(row, fields));
        }
        String next = reply.hasNonNull("next") ? reply.get("next").asText() : null;
        return new CrmContactPage(records, next);
    }

    @Override
    public List<CrmFieldOption> findContactFields(CrmConnection connection) {
        JsonNode result = call(connection, "crm.contact.fields", objectMapper.createObjectNode(), ErrorCode.CRM_CONTACT_SYNC_FAILED).path("result");
        List<CrmFieldOption> options = new ArrayList<>();
        result.fieldNames().forEachRemaining(id -> {
            JsonNode field = result.get(id);
            options.add(new CrmFieldOption(id, label(field, id), field.path("type").asText(null)));
        });
        return options;
    }

    /**
     * {@code crm.contact.update}. A multi-field ({@code EMAIL}, {@code WEB}, {@code IM}) gets the
     * value as one more entry — Bitrix24 adds to such a field rather than replacing it — and only
     * when the record does not hold it yet ({@code crm.contact.get}), so a repeated push adds nothing.
     */
    @Override
    public void updateContactFields(CrmConnection connection, String externalId, Map<String, String> values) {
        JsonNode current = values.keySet().stream().anyMatch(MULTI_FIELDS::contains)
                ? findContact(connection, externalId) : null;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", externalId);
        ObjectNode target = payload.putObject("fields");
        values.forEach((field, value) -> {
            if (!MULTI_FIELDS.contains(field)) {
                target.put(field, value);
            } else if (!holds(current.path(field), value)) {
                target.putArray(field).addObject().put("VALUE", value).put("VALUE_TYPE", "WORK");
            }
        });
        if (target.isEmpty()) {
            return;
        }
        JsonNode reply = call(connection, "crm.contact.update", payload, ErrorCode.CRM_CONTACT_PUSH_FAILED);
        if (!reply.path("result").asBoolean(false)) {
            throw new ExternalServiceException(ErrorCode.CRM_CONTACT_PUSH_FAILED, SERVICE, "crm.contact.update was not accepted");
        }
    }

    private JsonNode findContact(CrmConnection connection, String externalId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", externalId);
        return call(connection, "crm.contact.get", payload, ErrorCode.CRM_CONTACT_PUSH_FAILED).path("result");
    }

    /** Whether a multi-field already has an entry equal to {@code value}, ignoring case and spaces. */
    private static boolean holds(JsonNode entries, String value) {
        for (JsonNode entry : entries) {
            if (entry.path("VALUE").asText("").strip().equalsIgnoreCase(value.strip())) {
                return true;
            }
        }
        return false;
    }

    private CrmContactRecord toRecord(JsonNode row, Set<String> fields) {
        List<String> phones = new ArrayList<>();
        for (JsonNode phone : row.path("PHONE")) {
            String value = phone.path("VALUE").asText("").trim();
            if (!value.isEmpty()) {
                phones.add(value);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : fields) {
            String value = text(row.get(field));
            if (value != null) {
                values.put(field, value);
            }
        }
        String name = (row.path("NAME").asText("") + " " + row.path("LAST_NAME").asText("")).trim();
        return new CrmContactRecord(row.path("ID").asText(), name, phones, values, instant(row.path("DATE_MODIFY").asText(null)));
    }

    /** POSTs one REST method; the answer, or an {@link ExternalServiceException} saying why there is none. */
    private JsonNode call(CrmConnection connection, String method, ObjectNode payload, ErrorCode errorCode) {
        URI webhook = PublicUrlGuard.parsePublic(connection.baseUrl());
        if (webhook == null) {
            throw new ExternalServiceException(errorCode, SERVICE, "webhook address missing or not public");
        }
        if (connection.accessToken() != null && !connection.accessToken().isBlank()) {
            payload.put("auth", connection.accessToken());
        }
        try {
            String base = webhook.toString();
            URI url = URI.create((base.endsWith("/") ? base : base + "/") + method + ".json");
            PublicHttpResponse response = httpClient.send(PublicHttpRequest.create("POST", url)
                    .setTimeout(PAGE_TIMEOUT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .setBody(objectMapper.writeValueAsString(payload)));
            if (response.statusCode() / 100 != 2) {
                throw new ExternalServiceException(errorCode, SERVICE,
                        method + " answered HTTP " + response.statusCode());
            }
            JsonNode reply = objectMapper.readTree(response.readBodyText());
            if (reply.has("error")) {
                // The error code only: its description can echo the request back.
                throw new ExternalServiceException(errorCode, SERVICE,
                        method + " refused: " + reply.path("error").asText());
            }
            return reply;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(errorCode, SERVICE, e, method + " interrupted");
        } catch (Exception e) {
            // The type only, never the message: a message can quote the webhook URL.
            throw new ExternalServiceException(errorCode, SERVICE, e,
                    method + " failed: " + e.getClass().getSimpleName());
        }
    }

    /** A field's value as text: multi-fields ({@code EMAIL}) and lists joined by commas; objects are not text. */
    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isObject()) {
            return null;
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String part = item.isObject() ? item.path("VALUE").asText("") : item.asText("");
                if (!part.isBlank()) {
                    parts.add(part.trim());
                }
            }
            return parts.isEmpty() ? null : String.join(", ", parts);
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String label(JsonNode field, String id) {
        for (String name : List.of("listLabel", "formLabel", "title")) {
            String value = field.path(name).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return id;
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
