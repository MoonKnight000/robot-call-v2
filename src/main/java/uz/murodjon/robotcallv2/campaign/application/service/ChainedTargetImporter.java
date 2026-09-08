package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.campaign.application.dto.ParsedTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSourceStep;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSourceStepFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches a campaign's call list from a company's own API in several steps
 * ({@link TargetSource} with provider {@code CHAINED}).
 *
 * <p>{@link TargetApiImporter} covers the case where one request answers with rows that
 * already carry a phone number. Real CRMs frequently do not: the endpoint that knows who
 * is overdue answers with contract ids, and the number is two lookups away. This runs the
 * steps a company configured — one that lists, then any number called once per row — and
 * hands back the same {@link ParsedTarget}s the single-request importer does, so
 * everything downstream is unchanged.
 *
 * <p>Failure is split deliberately. The <em>list</em> step failing raises, exactly as in
 * {@link TargetApiImporter}: importing over a half-read list would quietly shrink a
 * campaign, and with {@code replaceTargets} it would empty one. An <em>enrichment</em>
 * step failing for one row drops that row into {@code errors} and the rest continue — a
 * single client whose record was deleted is not a reason to call nobody today.
 */
@Component
public class ChainedTargetImporter {

    private static final Logger log = LoggerFactory.getLogger(ChainedTargetImporter.class);

    /** Generous: nobody is on the line waiting for this. */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    /**
     * How many rows the list step may produce in total. Every one of them costs a request
     * per enrichment step, so this is also the ceiling on how long a sync can run.
     */
    private static final int MAX_ROWS = 5_000;

    /** Pages of the list step, when it paginates. */
    private static final int MAX_PAGES = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            // Never followed, as everywhere else here: the screening below applies to the
            // host that was configured, and a redirect is an endpoint choosing a second
            // one after that check has already passed.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * @param authHeaderValue the decrypted secret, sent on every step, or null when the
     *                        source sends no header
     * @param errors          rows dropped by an enrichment step, one entry each
     */
    public List<ParsedTarget> fetchTargets(TargetSource source, String authHeaderValue,
                                           List<CsvRowError> errors) {
        // One header for the whole chain: a source is one service, and the steps are its
        // endpoints. Nothing here needs a second credential.
        String authHeaderName = source.authHeaderName();
        List<TargetSourceStep> steps = source.steps();
        if (steps == null || steps.isEmpty()) {
            throw new ValidationException(ErrorCode.TARGET_SOURCE_STEPS_REQUIRED);
        }
        TargetSourceStep listStep = steps.getFirst();
        List<TargetSourceStep> enrichments = steps.subList(1, steps.size());

        List<Map<String, Object>> rows = listRows(listStep, authHeaderName, authHeaderValue);
        List<ParsedTarget> targets = new ArrayList<>();
        int line = 0;
        for (Map<String, Object> row : rows) {
            line++;
            if (!enrich(row, enrichments, authHeaderName, authHeaderValue, line, errors)) {
                continue;
            }
            String phone = text(row.get(source.phoneFieldOrDefault()));
            if (phone == null || phone.isBlank()) {
                errors.add(new CsvRowError(line, "no '" + source.phoneFieldOrDefault() + "' after the last step"));
                continue;
            }
            targets.add(new ParsedTarget(line, clientId(row, source.clientIdField()), phone,
                    text(row.get(source.languageField())), facts(row, source)));
        }
        log.info("Chained target source for campaign {}: {} row(s) listed, {} usable, {} rejected",
                source.campaignId(), rows.size(), targets.size(), errors.size());
        return targets;
    }

    /**
     * The list step, run once or until the pages run out. Raises on any failure — see the
     * class comment for why this half does not fail open.
     */
    private List<Map<String, Object>> listRows(TargetSourceStep step, String authHeaderName,
                                              String authHeaderValue) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int pages = step.paginate() ? MAX_PAGES : 1;
        for (int page = 1; page <= pages && rows.size() < MAX_ROWS; page++) {
            Map<String, Object> pageVariables = Map.of("page", page);
            JsonNode body = requireJson(step, pageVariables, authHeaderName, authHeaderValue);
            JsonNode items = itemsOf(body, step.itemsPath());
            if (items.isEmpty()) {
                break;
            }
            int kept = 0;
            for (JsonNode item : items) {
                if (rows.size() >= MAX_ROWS) {
                    break;
                }
                if (!item.isObject() || !matches(item, step.filters())) {
                    continue;
                }
                rows.add(extract(item, step.extract()));
                kept++;
            }
            log.debug("Chained list step '{}' page {}: {} row(s), {} kept", step.name(), page, items.size(), kept);
        }
        return rows;
    }

    /**
     * Runs every enrichment step for one row, adding what each extracts to that row's
     * variables so a later step can use it.
     *
     * @return whether the row survived; a step that could not be called or that produced
     *         nothing usable rejects it
     */
    private boolean enrich(Map<String, Object> row, List<TargetSourceStep> steps, String authHeaderName,
                           String authHeaderValue, int line, List<CsvRowError> errors) {
        for (TargetSourceStep step : steps) {
            JsonNode body;
            try {
                body = requireJson(step, row, authHeaderName, authHeaderValue);
            } catch (RuntimeException e) {
                errors.add(new CsvRowError(line, "step '" + step.name() + "': " + e.getMessage()));
                return false;
            }
            row.putAll(extract(body, step.extract()));
        }
        return true;
    }

    /** One request, with {@code {{name}}} filled from {@code variables}. */
    private JsonNode requireJson(TargetSourceStep step, Map<String, Object> variables,
                                 String authHeaderName, String authHeaderValue) {
        String url = renderUrl(step.url(), variables);
        URI uri = PublicUrlGuard.parsePublic(url);
        if (uri == null) {
            throw new ValidationException(ErrorCode.TARGET_SOURCE_URL_INVALID, step.url());
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json");
            applyAuthHeader(request, authHeaderName, authHeaderValue);
            if (step.method() == TargetSourceMethod.POST) {
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                renderBody(step.body(), variables), StandardCharsets.UTF_8));
            } else {
                request.GET();
            }

            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_FETCH_FAILED, "target-source",
                        uri.getHost(), "step '" + step.name() + "' answered HTTP " + response.statusCode());
            }
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_FETCH_FAILED, "target-source",
                        uri.getHost(), "step '" + step.name() + "' answered with an empty body");
            }
            if (responseBody.length() > MAX_RESPONSE_BYTES) {
                throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_FETCH_FAILED, "target-source",
                        uri.getHost(), "step '" + step.name() + "' answered with more than "
                        + MAX_RESPONSE_BYTES + " bytes");
            }
            return objectMapper.readTree(responseBody);
        } catch (ExternalServiceException | ValidationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_FETCH_FAILED, "target-source",
                    uri.getHost(), "interrupted");
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_FETCH_FAILED, "target-source",
                    e, uri.getHost(), e.getMessage());
        }
    }

    /** The rows of a list step's answer. */
    private JsonNode itemsOf(JsonNode body, String itemsPath) {
        JsonNode node = path(body, itemsPath);
        if (!node.isArray()) {
            throw new ValidationException(ErrorCode.TARGET_SOURCE_RESPONSE_INVALID,
                    itemsPath == null || itemsPath.isBlank()
                            ? "the body is not an array — set itemsPath"
                            : "nothing at itemsPath '" + itemsPath + "'");
        }
        return node;
    }

    private static boolean matches(JsonNode row, List<TargetSourceStepFilter> filters) {
        for (TargetSourceStepFilter filter : filters) {
            if (!matches(row, filter)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(JsonNode row, TargetSourceStepFilter filter) {
        JsonNode node = path(row, filter.path());
        boolean present = !node.isMissingNode() && !node.isNull() && !node.asText("").isBlank();
        return switch (filter.operator()) {
            case PRESENT -> present;
            case ABSENT -> !present;
            case EQ -> present && node.asText().equalsIgnoreCase(filter.value());
            case NE -> !present || !node.asText().equalsIgnoreCase(filter.value());
            case GT, GTE, LT, LTE -> compares(node, filter);
        };
    }

    /** Numeric comparison. A value or operand that is not a number never matches. */
    private static boolean compares(JsonNode node, TargetSourceStepFilter filter) {
        BigDecimal left = number(node);
        BigDecimal right = number(filter.value());
        if (left == null || right == null) {
            return false;
        }
        int comparison = left.compareTo(right);
        return switch (filter.operator()) {
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            default -> false;
        };
    }

    /** {@code name -> value} for every path this step declared that resolves to something. */
    private static Map<String, Object> extract(JsonNode node, Map<String, String> paths) {
        Map<String, Object> values = new LinkedHashMap<>();
        paths.forEach((name, path) -> {
            JsonNode value = path(node, path);
            if (value.isMissingNode() || value.isNull()) {
                return;
            }
            if (value.isNumber()) {
                values.put(name, value.decimalValue());
            } else if (value.isBoolean()) {
                values.put(name, value.booleanValue());
            } else if (value.isValueNode()) {
                values.put(name, value.asText());
            }
            // Objects and arrays are skipped: a fact is one line of a prompt, and a phone
            // number is not a subtree.
        });
        return values;
    }

    /**
     * Everything the chain collected except the three keys the source mapped, as the
     * target's {@code context_data} — the same rule the single-request importer follows.
     */
    private String facts(Map<String, Object> row, TargetSource source) {
        ObjectNode facts = objectMapper.createObjectNode();
        row.forEach((key, value) -> {
            if (key.equals(source.phoneFieldOrDefault())
                    || key.equals(source.clientIdField())
                    || key.equals(source.languageField())) {
                return;
            }
            switch (value) {
                case BigDecimal number -> facts.put(key, number);
                case Boolean flag -> facts.put(key, flag);
                case null -> { }
                default -> facts.put(key, value.toString());
            }
        });
        return facts.toString();
    }

    /**
     * A dot path, where a numeric segment indexes an array: {@code "data.contacts.0.phones.0"}.
     * An empty path is the node itself.
     */
    private static JsonNode path(JsonNode root, String path) {
        if (path == null || path.isBlank()) {
            return root;
        }
        JsonNode node = root;
        for (String segment : path.trim().split("\\.")) {
            node = segment.chars().allMatch(Character::isDigit)
                    ? node.path(Integer.parseInt(segment))
                    : node.path(segment);
        }
        return node;
    }

    private static String renderUrl(String template, Map<String, Object> variables) {
        return render(template, variables, value -> URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    /**
     * The body template with its placeholders filled and JSON-escaped: a client name with
     * a quote in it must not end the request early.
     */
    private String renderBody(String template, Map<String, Object> variables) {
        if (template == null || template.isBlank()) {
            return "{}";
        }
        return render(template, variables, this::jsonEscape);
    }

    private static String render(String template, Map<String, Object> variables,
                                 java.util.function.UnaryOperator<String> escape) {
        if (template == null) {
            return "";
        }
        String rendered = template;
        for (Map.Entry<String, Object> variable : variables.entrySet()) {
            String placeholder = "{{" + variable.getKey() + "}}";
            if (rendered.contains(placeholder)) {
                rendered = rendered.replace(placeholder,
                        variable.getValue() != null ? escape.apply(variable.getValue().toString()) : "");
            }
        }
        return rendered;
    }

    private String jsonEscape(String value) {
        try {
            String quoted = objectMapper.writeValueAsString(value);
            return quoted.substring(1, quoted.length() - 1);
        } catch (Exception e) {
            return "";
        }
    }

        private static long clientId(Map<String, Object> row, String field) {
        if (field == null || field.isBlank()) {
            return 0L;
        }
        Object value = row.get(field);
        if (value instanceof BigDecimal number) {
            return number.longValue();
        }
        try {
            return value != null ? Long.parseLong(value.toString().trim()) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String text(Object value) {
        return value != null ? value.toString().trim() : null;
    }

    private static BigDecimal number(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : number(node.asText(null));
    }

    private static BigDecimal number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void applyAuthHeader(HttpRequest.Builder request, String name, String value) {
        // A newline in a header value splits the request in two, and both halves come from
        // a tenant-edited settings page.
        if (name == null || value == null || name.isBlank() || value.isBlank()
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return;
        }
        request.header(name.trim(), value);
    }
}
