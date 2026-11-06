package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * amoCRM / Kommo contacts through the account's long-lived token: {@code GET /api/v4/contacts}
 * filtered by {@code updated_at}, 250 to a page, and {@code /api/v4/contacts/custom_fields}.
 * A field is matched by its id or by its code ({@code EMAIL}). The write-back is
 * {@code PATCH /api/v4/contacts/{id}} with {@code custom_fields_values}.
 */
@Component
public class AmoCrmContactSyncAdapter implements CrmContactSourcePort, CrmContactWriterPort {

    private static final String SERVICE = "amocrm";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration PAGE_TIMEOUT = Duration.ofSeconds(15);
    private static final int PAGE_SIZE = 250;
    private static final String PHONE_CODE = "PHONE";

    private final PublicHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AmoCrmContactSyncAdapter(ObjectMapper objectMapper) {
        this(PublicHttpClient.create(CONNECT_TIMEOUT, PAGE_TIMEOUT), objectMapper);
    }

    AmoCrmContactSyncAdapter(PublicHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isSupported(CrmProvider provider) {
        return provider == CrmProvider.AMOCRM || provider == CrmProvider.KOMMO;
    }

    @Override
    public CrmContactPage findContactsModifiedSince(CrmConnection connection, Instant since, String pageToken,
                                                    Set<String> fields) {
        int page = pageToken == null ? 1 : Integer.parseInt(pageToken);
        StringBuilder query = new StringBuilder("/api/v4/contacts?limit=").append(PAGE_SIZE)
                .append("&page=").append(page)
                .append("&order%5Bupdated_at%5D=asc");
        if (since != null) {
            query.append("&filter%5Bupdated_at%5D%5Bfrom%5D=").append(since.getEpochSecond());
        }
        JsonNode reply = send(connection, "GET", query.toString(), null, ErrorCode.CRM_CONTACT_SYNC_FAILED);
        if (reply == null) {
            return CrmContactPage.last(List.of());
        }
        List<CrmContactRecord> records = new ArrayList<>();
        for (JsonNode contact : reply.path("_embedded").path("contacts")) {
            records.add(toRecord(contact, fields));
        }
        boolean more = reply.path("_links").hasNonNull("next");
        return new CrmContactPage(records, more ? String.valueOf(page + 1) : null);
    }

    @Override
    public List<CrmFieldOption> findContactFields(CrmConnection connection) {
        JsonNode reply = send(connection, "GET", "/api/v4/contacts/custom_fields?limit=" + PAGE_SIZE, null,
                ErrorCode.CRM_CONTACT_SYNC_FAILED);
        List<CrmFieldOption> options = new ArrayList<>();
        if (reply == null) {
            return options;
        }
        for (JsonNode field : reply.path("_embedded").path("custom_fields")) {
            options.add(new CrmFieldOption(field.path("id").asText(), field.path("name").asText(""),
                    field.path("type").asText(null)));
        }
        return options;
    }

    /**
     * {@code PATCH /api/v4/contacts/{id}}. A numeric field is a custom field id, anything else
     * a field code ({@code EMAIL}); the value replaces what the field held.
     */
    @Override
    public void updateContactFields(CrmConnection connection, String externalId, Map<String, String> values) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode fieldValues = payload.putArray("custom_fields_values");
        values.forEach((field, value) -> {
            ObjectNode entry = fieldValues.addObject();
            if (field.chars().allMatch(Character::isDigit)) {
                entry.put("field_id", Long.parseLong(field));
            } else {
                entry.put("field_code", field);
            }
            entry.putArray("values").addObject().put("value", value);
        });
        try {
            send(connection, "PATCH", "/api/v4/contacts/" + Long.parseLong(externalId),
                    objectMapper.writeValueAsString(payload), ErrorCode.CRM_CONTACT_PUSH_FAILED);
        } catch (NumberFormatException e) {
            throw new ExternalServiceException(ErrorCode.CRM_CONTACT_PUSH_FAILED, SERVICE, "contact id is not numeric");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize an amoCRM contact update", e);
        }
    }

    private static CrmContactRecord toRecord(JsonNode contact, Set<String> fields) {
        List<String> phones = new ArrayList<>();
        Map<String, String> values = new LinkedHashMap<>();
        for (JsonNode field : contact.path("custom_fields_values")) {
            String id = field.path("field_id").asText();
            String code = field.path("field_code").asText(null);
            List<String> parts = new ArrayList<>();
            for (JsonNode value : field.path("values")) {
                String text = value.path("value").asText("").trim();
                if (!text.isEmpty()) {
                    parts.add(text);
                }
            }
            if (PHONE_CODE.equals(code)) {
                phones.addAll(parts);
            }
            if (parts.isEmpty()) {
                continue;
            }
            String joined = String.join(", ", parts);
            if (fields.contains(id)) {
                values.put(id, joined);
            }
            if (code != null && fields.contains(code)) {
                values.put(code, joined);
            }
        }
        long updatedAt = contact.path("updated_at").asLong(0);
        return new CrmContactRecord(contact.path("id").asText(), contact.path("name").asText("").trim(), phones,
                values, updatedAt > 0 ? Instant.ofEpochSecond(updatedAt) : null);
    }

    /** One request; the answer, {@code null} on 204 (amoCRM's "nothing matched"), else an {@link ExternalServiceException}. */
    private JsonNode send(CrmConnection connection, String method, String pathAndQuery, String body, ErrorCode errorCode) {
        URI account = connection.accessToken() == null || connection.accessToken().isBlank()
                ? null : PublicUrlGuard.parsePublic(connection.baseUrl());
        if (account == null) {
            throw new ExternalServiceException(errorCode, SERVICE,
                    "account address or access token missing, or the address is not public");
        }
        try {
            // Scheme and host only: whatever page of the account was pasted, the API is at its root.
            URI url = URI.create(account.getScheme() + "://" + account.getRawAuthority() + pathAndQuery);
            PublicHttpRequest request = PublicHttpRequest.create(method, url)
                    .setTimeout(PAGE_TIMEOUT)
                    .addHeader("Authorization", "Bearer " + connection.accessToken())
                    .addHeader("Accept", "application/json");
            if (body != null) {
                request.addHeader("Content-Type", "application/json").setBody(body);
            }
            PublicHttpResponse response = httpClient.send(request);
            if (response.statusCode() == 204) {
                return null;
            }
            if (response.statusCode() / 100 != 2) {
                throw new ExternalServiceException(errorCode, SERVICE,
                        "answered HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.readBodyText());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(errorCode, SERVICE, e, "interrupted");
        } catch (Exception e) {
            // The type only, never the message: a message can quote the tenant's URL.
            throw new ExternalServiceException(errorCode, SERVICE, e,
                    e.getClass().getSimpleName());
        }
    }
}
