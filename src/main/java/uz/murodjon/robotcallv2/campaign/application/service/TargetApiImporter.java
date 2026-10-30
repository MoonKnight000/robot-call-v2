package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.campaign.application.dto.ParsedTarget;
import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceFetcherPort;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Fetches a campaign's call list from the company's own API ({@link TargetSource}).
 *
 * <p>Unlike the fact webhook, this one does not fail open. A fact webhook that is down
 * costs one call its freshest numbers; a target source that is down would, if ignored,
 * silently start a recurring campaign over yesterday's list — or over an empty one when
 * {@code replaceTargets} is set. So every failure raises, the campaign's targets are left
 * exactly as they were, and the reason is stamped on the source for the campaign page.
 *
 * <p>The response is a JSON array of rows, either as the whole body or at {@code itemsPath}.
 * Each row's phone, client id and language are read from the configured keys; every other
 * key in the row becomes one of that target's facts, which the scenario's {@code factSchema}
 * then filters down to what the agent may actually say.
 */
@Component
public class TargetApiImporter {

    private static final Logger log = LoggerFactory.getLogger(TargetApiImporter.class);

    private static final int MAX_ROWS = 100_000;

    private final ObjectMapper objectMapper;
    private final TargetSourceFetcherPort targetSourceFetcherPort;

    public TargetApiImporter(TargetSourceFetcherPort targetSourceFetcherPort, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.targetSourceFetcherPort = targetSourceFetcherPort;
    }

    /**
     * @param source        what to call and how to read the answer
     * @param authHeaderValue the decrypted secret, or null when the source sends no header
     * @param errors        rejected rows are appended here, one entry per row, so the caller
     *                      can report which of them the endpoint got wrong
     */
    public List<ParsedTarget> fetchTargets(TargetSource source, String authHeaderValue,
                                           List<CsvRowError> errors) {
        URI uri = requireCallableUri(source.url());
        String body = send(uri, source, authHeaderValue);
        return parseTargets(body, source, errors, uri.getHost());
    }

    /**
     * Reads the rows out of an answer already in hand, the way {@link #fetchTargets} does for one it
     * fetched itself — the same {@code itemsPath} and field names, whoever made the request.
     *
     * @param origin where the answer came from, for the log
     */
    public List<ParsedTarget> parseTargets(String body, TargetSource source, List<CsvRowError> errors,
                                           String origin) {
        JsonNode rows = readRows(body, source.itemsPath());

        List<ParsedTarget> targets = new ArrayList<>();
        int index = 0;
        for (JsonNode row : rows) {
            index++;
            if (index > MAX_ROWS) {
                errors.add(new CsvRowError(index, "response has more than " + MAX_ROWS + " rows"));
                break;
            }
            if (!row.isObject()) {
                errors.add(new CsvRowError(index, "row is not a JSON object"));
                continue;
            }
            String phone = text(row, source.phoneFieldOrDefault());
            if (phone == null || phone.isBlank()) {
                errors.add(new CsvRowError(index, "no '" + source.phoneFieldOrDefault() + "' in row"));
                continue;
            }
            targets.add(new ParsedTarget(index, clientId(row, source.clientIdField()), phone,
                    text(row, source.languageField()), facts(row, source)));
        }
        log.info("Target source {} returned {} row(s) for campaign {}: {} usable, {} rejected",
                origin, index, source.campaignId(), targets.size(), errors.size());
        return targets;
    }

    private String send(URI uri, TargetSource source, String authHeaderValue) {
        TargetSourceMethod method = source.methodOrDefault();
        String requestBody = method == TargetSourceMethod.POST
                ? (source.requestBody() != null ? source.requestBody() : "{}")
                : null;
        return targetSourceFetcherPort.fetch(uri, method, requestBody, source.authHeaderName(), authHeaderValue);
    }

    private JsonNode readRows(String body, String itemsPath) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new ValidationException(ErrorCode.TARGET_SOURCE_RESPONSE_NOT_JSON);
        }
        JsonNode node = root;
        if (itemsPath != null && !itemsPath.isBlank()) {
            for (String segment : itemsPath.trim().split("\\.")) {
                node = node.path(segment);
            }
        }
        if (!node.isArray()) {
            throw itemsPath == null || itemsPath.isBlank()
                    ? new ValidationException(ErrorCode.TARGET_SOURCE_RESPONSE_NOT_ARRAY)
                    : new ValidationException(ErrorCode.TARGET_SOURCE_ITEMS_PATH_EMPTY, itemsPath);
        }
        return node;
    }

    /** Everything in the row except the three mapped keys, as the target's context_data. */
    private String facts(JsonNode row, TargetSource source) {
        ObjectNode facts = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if (key.equals(source.phoneFieldOrDefault())
                    || key.equals(source.clientIdField())
                    || key.equals(source.languageField())) {
                continue;
            }
            facts.set(key, field.getValue());
        }
        return facts.toString();
    }

    private static long clientId(JsonNode row, String field) {
        if (field == null || field.isBlank()) {
            return 0L;
        }
        JsonNode value = row.path(field);
        if (value.isNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText("0").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String text(JsonNode row, String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        JsonNode value = row.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText().trim();
    }


    /**
     * Whether this server is willing to call that host on a tenant's behalf. Without this
     * the feature is a request forger: {@code http://127.0.0.1:8080/actuator} or any
     * address on the Docker network would be fetched by this process from inside the
     * perimeter, and the answer turned into a list of numbers to dial.
     */
    private static URI requireCallableUri(String url) {
        URI uri = PublicUrlGuard.parsePublic(url);
        if (uri == null) {
            throw new ValidationException(ErrorCode.TARGET_SOURCE_URL_INVALID, url);
        }
        return uri;
    }
}
