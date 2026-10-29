package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.application.port.output.UysotCatalogPort;
import uz.murodjon.robotcallv2.crm.domain.entity.*;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.crm.infrastructure.config.UysotCatalogProperties;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * {@link UysotCatalogPort} over the Uysot Open API v1.
 *
 * <p>The same envelope as every other Open API answer ({@code {data, accept, ...}}), and a
 * list may come back bare, as a page ({@code data.data}) or as Spring's {@code data.content}
 * — {@link #rows} accepts all three, and each field is read under the names the Open API and
 * Uysot's own reports use for it, so a renamed column degrades to a missing value instead
 * of a failed call.
 *
 * <p>Reads run while a caller waits on the agent's answer, hence the short timeout.
 */
@Component
public class HttpClientUysotCatalogAdapter implements UysotCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(HttpClientUysotCatalogAdapter.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final String SERVICE = "uysot";
    private static final List<String> ACTIVE_CONTRACT_STATUSES = List.of("ACTIVE", "STARTED");
    private static final int PHONE_SUFFIX_DIGITS = 9;
    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");

    private final CrmProperties crmProperties;
    private final UysotCatalogProperties catalogProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public HttpClientUysotCatalogAdapter(CrmProperties crmProperties, UysotCatalogProperties catalogProperties,
                                         ObjectMapper objectMapper) {
        this.crmProperties = crmProperties;
        this.catalogProperties = catalogProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CrmHouse> findHouses(CrmConnection connection) {
        JsonNode response = get(connection, require(catalogProperties.housesPath(), "houses-path"));
        List<CrmHouse> houses = new ArrayList<>();
        for (JsonNode row : rows(response)) {
            houses.add(new CrmHouse(row.path("id").asLong(), text(row, "name"),
                    row.path("sale").asBoolean(true),
                    firstText(row, "branchName", "branch.name"),
                    firstText(row, "currencyCcy", "currency.ccy", "currency")));
        }
        return houses;
    }

    @Override
    public List<CrmFlat> searchFlats(CrmConnection connection, FlatSearchFilter filter) {
        String path = require(catalogProperties.flatFilterPath(), "flat-filter-path");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("size", filter.limit());
        body.put("buildingSale", true);
        body.putArray("statuses").add("SALE");
        if (filter.rooms() != null) {
            body.putArray("rooms").add(String.valueOf(filter.rooms()));
        }
        if (filter.priceFrom() != null) {
            body.put("priceFrom", filter.priceFrom());
        }
        if (filter.priceTo() != null) {
            body.put("priceTo", filter.priceTo());
        }
        if (filter.houseId() != null) {
            body.putArray("houseIds").add(filter.houseId());
        }
        if (filter.floorFrom() != null) {
            body.put("floorFrom", filter.floorFrom());
        }
        if (filter.floorTo() != null) {
            body.put("floorTo", filter.floorTo());
        }
        body.put("sort", "PRICE_NOT_REPAIRED");
        body.put("order", "ASC");
        List<CrmFlat> flats = new ArrayList<>();
        for (JsonNode row : rows(post(connection, path, body))) {
            flats.add(new CrmFlat(
                    row.path("id").asLong(),
                    firstText(row, "houseName", "house.name"),
                    firstText(row, "buildingName", "building.name"),
                    text(row, "number"),
                    integer(row, "rooms"),
                    decimal(row, "area"),
                    integer(row, "floor"),
                    firstDecimal(row, "priceNotRepaired", "price"),
                    firstDecimal(row, "priceRepaired"),
                    firstDecimal(row, "pricePerArea"),
                    firstText(row, "currencyCcy", "currency.ccy", "currency"),
                    text(row, "status")));
        }
        // Ordered here as well: an API that ignored the sort must not put the dearest flat first.
        flats.sort(Comparator.comparing(CrmFlat::price, Comparator.nullsLast(Comparator.naturalOrder())));
        return flats;
    }

    @Override
    public Optional<CrmContract> findContractByNumber(CrmConnection connection, String contractNumber) {
        ObjectNode body = contractFilter();
        body.put("search", contractNumber.trim());
        for (JsonNode row : rows(post(connection, crmProperties.contractFilterPath(), body))) {
            if (contractNumber.trim().equalsIgnoreCase(text(row, "number"))) {
                return Optional.of(toContract(row));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<CrmContract> findActiveContractsByPhone(CrmConnection connection, String phone) {
        ObjectNode body = contractFilter();
        body.put("search", phoneSuffix(phone));
        List<CrmContract> contracts = new ArrayList<>();
        for (JsonNode row : rows(post(connection, crmProperties.contractFilterPath(), body))) {
            contracts.add(toContract(row));
        }
        return contracts;
    }

    @Override
    public List<CrmPaymentDue> findPaymentSchedule(CrmConnection connection, long contractId) {
        String path = require(catalogProperties.contractPaymentPath(), "contract-payment-path")
                .replace("{id}", String.valueOf(contractId));
        JsonNode schedule = activeSchedule(rows(get(connection, path)));
        List<CrmPaymentDue> dues = new ArrayList<>();
        if (schedule == null) {
            return dues;
        }
        JsonNode parts = firstArray(schedule, "parts", "monthlyPaymentParts", "items", "rows");
        for (JsonNode part : parts) {
            BigDecimal amount = firstDecimal(part, "amount");
            BigDecimal remaining = firstDecimal(part, "unpaidAmount", "residue", "remaining");
            BigDecimal paidAmount = firstDecimal(part, "paidAmount", "payedAmount", "payed");
            if (remaining == null && amount != null && paidAmount != null) {
                remaining = amount.subtract(paidAmount).max(BigDecimal.ZERO);
            }
            boolean paid = part.has("paid") ? part.path("paid").asBoolean()
                    : "PAID".equalsIgnoreCase(firstText(part, "paidStatus", "status"));
            dues.add(new CrmPaymentDue(date(part, "date", "paymentDate", "dueDate"), amount, remaining, paid));
        }
        dues.sort(Comparator.comparing(CrmPaymentDue::date, Comparator.nullsLast(Comparator.naturalOrder())));
        return dues;
    }

    @Override
    public List<CrmEmployee> findEmployees(CrmConnection connection) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("size", 200);
        List<CrmEmployee> employees = new ArrayList<>();
        for (JsonNode row : rows(post(connection, require(catalogProperties.employeeFilterPath(),
                "employee-filter-path"), body))) {
            String name = firstText(row, "fullName", "name");
            if (name == null) {
                name = String.join(" ", Objects.requireNonNullElse(text(row, "firstName"), ""),
                        Objects.requireNonNullElse(text(row, "lastName"), "")).trim();
            }
            employees.add(new CrmEmployee(row.path("id").asLong(), name, firstText(row, "phone", "phoneNumber"),
                    firstText(row, "departmentName", "department.name")));
        }
        return employees;
    }

    @Override
    public List<CrmPipe> findPipes(CrmConnection connection) {
        List<CrmPipe> pipes = new ArrayList<>();
        for (JsonNode row : rows(get(connection, require(catalogProperties.pipePath(), "pipe-path")))) {
            List<CrmPipeStatus> statuses = new ArrayList<>();
            for (JsonNode status : firstArray(row, "statuses", "pipeStatuses", "pipeStatusList")) {
                statuses.add(new CrmPipeStatus(status.path("id").asLong(), text(status, "name")));
            }
            pipes.add(new CrmPipe(row.path("id").asLong(), text(row, "name"), statuses));
        }
        return pipes;
    }

    private ObjectNode contractFilter() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("size", 20);
        ArrayNode statuses = body.putArray("statuses");
        ACTIVE_CONTRACT_STATUSES.forEach(statuses::add);
        return body;
    }

    private static CrmContract toContract(JsonNode row) {
        long leadId = row.path("lead").path("id").asLong(row.path("leadId").asLong(0));
        long responsibleId = row.path("responsibleBy").path("id").asLong(row.path("responsibleById").asLong(0));
        return new CrmContract(
                row.path("id").asLong(),
                text(row, "number"),
                firstText(row, "client.name", "clientName"),
                firstDecimal(row, "amount"),
                firstDecimal(row, "payedAmount", "paidAmount"),
                firstDecimal(row, "residue"),
                firstText(row, "currency.ccy", "currencyCcy", "currency"),
                row.path("delay").asInt(0),
                text(row, "status"),
                leadId > 0 ? leadId : null,
                responsibleId > 0 ? responsibleId : null,
                firstText(row, "responsibleBy.fullName", "responsibleBy.name", "responsibleByName"));
    }

    /** The schedule that is in force: one marked ACTIVE, or the only one there is. */
    private static JsonNode activeSchedule(List<JsonNode> schedules) {
        for (JsonNode schedule : schedules) {
            if ("ACTIVE".equalsIgnoreCase(text(schedule, "status"))) {
                return schedule;
            }
        }
        return schedules.size() == 1 ? schedules.getFirst() : null;
    }

    private static String require(String path, String key) {
        if (path == null || path.isBlank()) {
            throw new ExternalServiceException(ErrorCode.UYSOT_ENDPOINT_NOT_CONFIGURED, SERVICE, key);
        }
        return path;
    }

    private JsonNode get(CrmConnection connection, String path) {
        return send(connection, path, HttpRequest.newBuilder().GET());
    }

    private JsonNode post(CrmConnection connection, String path, JsonNode body) {
        try {
            return send(connection, path, HttpRequest.newBuilder()
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("a filter body could not be serialised", e);
        }
    }

    private JsonNode send(CrmConnection connection, String path, HttpRequest.Builder request) {
        request.uri(URI.create(resolve(connection.baseUrl(), path)))
                .timeout(TIMEOUT)
                .header("Accept", "application/json");
        // X-Open-Api-Token only: the Open API ignores Authorization: Bearer.
        if (connection.accessToken() != null) {
            request.header("X-Open-Api-Token", connection.accessToken());
        }
        HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.UYSOT_REQUEST_FAILED, SERVICE, e, path, "interrupted");
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.UYSOT_REQUEST_FAILED, SERVICE, e, path, e.getMessage());
        }
        if (response.statusCode() / 100 != 2) {
            log.warn("Uysot {} answered HTTP {}", path, response.statusCode());
            throw new ExternalServiceException(ErrorCode.UYSOT_REQUEST_FAILED, SERVICE, path,
                    "HTTP " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.UYSOT_REQUEST_FAILED, SERVICE, e, path, "unreadable body");
        }
    }

    /** The list an answer carries: {@code data} itself, {@code data.data} or {@code data.content}. */
    private static List<JsonNode> rows(JsonNode response) {
        JsonNode data = response == null ? null : response.has("data") ? response.get("data") : response;
        JsonNode list = data == null ? null
                : data.isArray() ? data
                : data.path("data").isArray() ? data.path("data")
                : data.path("content").isArray() ? data.path("content")
                : null;
        List<JsonNode> rows = new ArrayList<>();
        if (list != null) {
            list.forEach(rows::add);
        }
        return rows;
    }

    private static String resolve(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return base + (path.startsWith("/") ? path.substring(1) : path);
    }

    private static String phoneSuffix(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() > PHONE_SUFFIX_DIGITS ? digits.substring(digits.length() - PHONE_SUFFIX_DIGITS) : digits;
    }

    /** {@code field} or a dotted path into nested objects ({@code currency.ccy}). */
    private static JsonNode at(JsonNode node, String field) {
        JsonNode current = node;
        for (String part : field.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = at(node, field);
        if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = at(node, field);
        if (value.isNumber()) {
            return value.asInt();
        }
        try {
            String text = text(node, field);
            return text == null ? null : Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = at(node, field);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            String text = text(node, field);
            return text == null ? null : new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal value = decimal(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static JsonNode firstArray(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray()) {
                return value;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    /** {@code yyyy-MM-dd…} text, or epoch seconds — Uysot uses both. */
    private static LocalDate date(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return Instant.ofEpochSecond(value.asLong()).atZone(TASHKENT).toLocalDate();
            }
            String text = text(node, field);
            if (text != null && text.length() >= 10) {
                try {
                    return LocalDate.parse(text.substring(0, 10));
                } catch (Exception e) {
                    // try the next name
                }
            }
        }
        return null;
    }
}
