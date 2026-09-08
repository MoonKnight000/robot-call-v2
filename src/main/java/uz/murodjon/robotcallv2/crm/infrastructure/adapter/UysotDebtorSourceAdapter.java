package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.campaign.application.port.output.DebtorSourcePort;
import uz.murodjon.robotcallv2.crm.domain.entity.Debtor;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.crm.infrastructure.config.DebtorImportProperties;
import uz.murodjon.robotcallv2.integration.application.service.CrmIntegrationService;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Today's overdue clients, read out of Uysot's Open API.
 *
 * <p>Three reads per debtor, because no endpoint answers with both a debt and a phone:
 *
 * <ol>
 *   <li>{@code POST /v1/open-api/contract/filter} — a page of contracts in force. Each row
 *       carries {@code delay} (days behind) and {@code residue} (what is left to pay), and
 *       neither a phone nor a lead. There is no server-side filter for {@code delay}, so
 *       the page is filtered here.</li>
 *   <li>{@code GET /v1/open-api/contract/{id}} — the same contract expanded, which is the
 *       only place the contract's {@code lead} appears.</li>
 *   <li>{@code GET /v1/open-api/lead/{leadId}} — the lead's contacts, and with them the
 *       phone numbers.</li>
 * </ol>
 *
 * <p>Nothing here throws. A campaign's recurrence sweep serves every tenant, and one
 * contract whose lead was deleted must not cost the rest of the list — a row that cannot
 * be resolved to a number is skipped and counted in the log line at the end.
 *
 * <p>The cost is why {@link DebtorImportProperties} exists: at 60 requests a minute, and
 * two extra requests per debtor, a list of two hundred is already the better part of ten
 * minutes. It runs before the campaign is activated, so that time is spent before the
 * first call rather than during the day.
 */
@Component
public class UysotDebtorSourceAdapter implements DebtorSourcePort {

    private static final Logger log = LoggerFactory.getLogger(UysotDebtorSourceAdapter.class);

    /** Nobody is on the line waiting for this, unlike the lookups {@code CrmClient} makes. */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Contracts still in force. A cancelled or finished one has nothing to collect on. */
    private static final List<String> ACTIVE_STATUSES = List.of("ACTIVE", "STARTED");

    private final CrmProperties crmProperties;
    private final DebtorImportProperties debtorImportProperties;
    private final CrmIntegrationService integrations;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public UysotDebtorSourceAdapter(CrmProperties crmProperties, DebtorImportProperties debtorImportProperties,
                                    CrmIntegrationService integrations) {
        this.crmProperties = crmProperties;
        this.debtorImportProperties = debtorImportProperties;
        this.integrations = integrations;
    }

    @Override
    public List<Debtor> findOverdueDebtors(long companyId, int limit) {
        if (!crmProperties.enabled() || crmProperties.baseUrl() == null || crmProperties.baseUrl().isBlank()) {
            log.warn("Uysot debtor import skipped: voice-agent.crm is not configured");
            return List.of();
        }
        int wanted = Math.min(limit > 0 ? limit : debtorImportProperties.maxDebtors(),
                debtorImportProperties.maxDebtors());
        List<Debtor> debtors = new ArrayList<>();
        int scanned = 0;
        int unreachable = 0;

        for (int page = 1; page <= debtorImportProperties.maxPages() && debtors.size() < wanted; page++) {
            JsonNode rows = contractPage(companyId, page);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (JsonNode row : rows) {
                scanned++;
                if (!overdue(row)) {
                    continue;
                }
                Debtor debtor = resolve(companyId, row);
                if (debtor == null) {
                    unreachable++;
                    continue;
                }
                debtors.add(debtor);
                if (debtors.size() >= wanted) {
                    break;
                }
            }
            if (rows.size() < debtorImportProperties.pageSize()) {
                break; // the last page: asking for another would only cost a request
            }
        }

        // Ordered here rather than by the API: contract/filter's sort keys do not include
        // the delay, so this orders what was collected, not the whole book.
        debtors.sort(Comparator.comparingInt(Debtor::delayDays).reversed());
        log.info("Uysot debtor import for company {}: {} contract(s) scanned, {} callable, "
                        + "{} overdue but unreachable", companyId, scanned, debtors.size(), unreachable);
        return debtors;
    }

    /** One page of contracts in force, or {@code null} when the API would not answer. */
    private JsonNode contractPage(long companyId, int page) {
        ObjectNode body = mapper.createObjectNode();
        body.put("page", page);
        body.put("size", debtorImportProperties.pageSize());
        ArrayNode statuses = body.putArray("statuses");
        ACTIVE_STATUSES.forEach(statuses::add);

        JsonNode response = post(companyId, crmProperties.contractFilterPath(), body);
        if (response == null) {
            return null;
        }
        // data is a PageableData: the rows are one level deeper.
        JsonNode rows = response.path("data").path("data");
        return rows.isArray() ? rows : null;
    }

    /** Whether this contract is behind by enough to be worth a call. */
    private boolean overdue(JsonNode contract) {
        int delay = contract.path("delay").asInt(0);
        BigDecimal residue = decimal(contract, "residue");
        return delay >= debtorImportProperties.minDelayDays()
                && residue != null && residue.signum() > 0;
    }

    /**
     * The two extra reads that turn an overdue contract into someone who can be called.
     *
     * @return {@code null} when the contract has no lead, the lead has no contact, or the
     *         contact has no number — none of which is an error worth failing the sync over
     */
    private Debtor resolve(long companyId, JsonNode contract) {
        long contractId = contract.path("id").asLong(0);
        if (contractId == 0) {
            return null;
        }
        JsonNode detail = get(companyId, crmProperties.contractPath().replace("{id}", String.valueOf(contractId)));
        if (detail == null) {
            return null;
        }
        long leadId = detail.path("data").path("lead").path("id").asLong(0);
        if (leadId == 0) {
            log.debug("Uysot contract {} has no lead — skipped", contractId);
            return null;
        }
        JsonNode lead = get(companyId, crmProperties.leadPath().replace("{id}", String.valueOf(leadId)));
        if (lead == null) {
            return null;
        }
        String phone = firstPhone(lead.path("data"));
        if (phone == null) {
            log.debug("Uysot lead {} has no contact phone — skipped", leadId);
            return null;
        }
        return new Debtor(
                leadId,
                phone,
                text(contract.path("client"), "name"),
                text(contract, "number"),
                decimal(contract, "residue"),
                text(contract.path("currency"), "ccy"),
                contract.path("delay").asInt(0));
    }

    /** The first number on the lead's first contact that has one. */
    private static String firstPhone(JsonNode lead) {
        for (JsonNode contact : lead.path("contacts")) {
            for (JsonNode phone : contact.path("phones")) {
                String value = phone.asText("").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private JsonNode get(long companyId, String path) {
        return send(companyId, HttpRequest.newBuilder()
                .uri(URI.create(resolve(path)))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET(), path);
    }

    private JsonNode post(long companyId, String path, ObjectNode body) {
        try {
            return send(companyId, HttpRequest.newBuilder()
                    .uri(URI.create(resolve(path)))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))), path);
        } catch (Exception e) {
            log.warn("Uysot {} could not be built: {}", path, e.getMessage());
            return null;
        }
    }

    private JsonNode send(long companyId, HttpRequest.Builder request, String path) {
        authToken(companyId).ifPresent(token -> request.header("X-Open-Api-Token", token));
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Uysot {} answered HTTP {}: {}", path, response.statusCode(), response.body());
                return null;
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Uysot {} interrupted", path);
            return null;
        } catch (Exception e) {
            log.warn("Uysot {} failed: {}", path, e.getMessage());
            return null;
        }
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

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText().trim();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }
}
