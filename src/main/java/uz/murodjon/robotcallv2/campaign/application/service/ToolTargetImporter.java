package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.application.dto.ParsedTarget;
import uz.murodjon.robotcallv2.campaign.application.dto.ToolTargetPreviewRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.ToolTargetPreviewResponse;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.tool.application.dto.ToolTestResponse;

import java.util.*;

/**
 * Fetches a campaign's call list by running one of the integration's tools
 * ({@link TargetSource} with provider {@code TOOL}).
 *
 * <p>The request, the address and the token live in the tool, which the platform admin defined once;
 * the campaign only names it and says how to read the answer. The answer is read from the tool's
 * <em>raw</em> body, as {@link TargetApiImporter} reads a body it fetched itself.
 */
@Component
public class ToolTargetImporter {

    private final IntegrationToolUseCase integrationToolUseCase;
    private final TargetApiImporter targetApiImporter;
    private final ObjectMapper objectMapper;

    public ToolTargetImporter(IntegrationToolUseCase integrationToolUseCase, TargetApiImporter targetApiImporter,
                              ObjectMapper objectMapper) {
        this.integrationToolUseCase = integrationToolUseCase;
        this.targetApiImporter = targetApiImporter;
        this.objectMapper = objectMapper;
    }

    /** @param errors rejected rows are appended here, one entry per row */
    public List<ParsedTarget> fetchTargets(long companyId, TargetSource source, List<CsvRowError> errors) {
        ToolTestResponse response = integrationToolUseCase.runEnabledReadTool(
                companyId, source.toolName(), source.toolArguments());
        if (response.raw() == null) {
            throw new ExternalServiceException(ErrorCode.TARGET_SOURCE_FETCH_FAILED, "integration",
                    "tool " + source.toolName(), "the tool returned no body");
        }
        return targetApiImporter.parseTargets(rawText(response.raw()), source, errors, "tool " + source.toolName());
    }

    /**
     * Executes the tool once outside any campaign and previews the parsed targets and detected dynamic variables.
     */
    public ToolTargetPreviewResponse previewTargets(long companyId, ToolTargetPreviewRequest request) {
        ToolTestResponse response = integrationToolUseCase.runEnabledReadTool(
                companyId, request.toolName().trim(), request.toolArguments());
        if (response == null || response.raw() == null) {
            return new ToolTargetPreviewResponse(0, List.of(), List.of(), List.of());
        }

        TargetSource tempSource = new TargetSource(
                0L, TargetSourceProvider.TOOL, null, null, null, null, null,
                request.itemsPath(), request.phoneFieldOrDefault(), request.clientIdField(),
                request.languageField(), false, false, true, List.of(),
                request.toolName().trim(), request.toolArguments(), null, null, null);

        List<CsvRowError> errors = new ArrayList<>();
        List<ParsedTarget> parsed = targetApiImporter.parseTargets(
                rawText(response.raw()), tempSource, errors, "tool " + request.toolName());

        LinkedHashSet<String> detectedVariables = new LinkedHashSet<>();
        List<Map<String, Object>> sampleTargets = new ArrayList<>();

        int sampleLimit = Math.min(parsed.size(), 10);
        for (int i = 0; i < sampleLimit; i++) {
            ParsedTarget pt = parsed.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("phone", pt.phone());
            if (pt.clientId() > 0) {
                map.put("clientId", pt.clientId());
            }
            if (pt.language() != null && !pt.language().isBlank()) {
                map.put("language", pt.language());
            }
            if (pt.contextJson() != null && !pt.contextJson().isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(pt.contextJson());
                    if (node.isObject()) {
                        node.fields().forEachRemaining(entry -> {
                            detectedVariables.add(entry.getKey());
                            if (entry.getValue().isValueNode()) {
                                map.put(entry.getKey(), entry.getValue().asText());
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
            sampleTargets.add(map);
        }

        return new ToolTargetPreviewResponse(parsed.size(), new ArrayList<>(detectedVariables), sampleTargets, errors);
    }

    /** The body as JSON text; a body that was not JSON stays text and fails to parse there, as it should. */
    private String rawText(Object raw) {
        if (raw instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("a parsed JSON body could not be written back", e);
        }
    }
}
