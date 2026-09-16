package uz.murodjon.robotcallv2.tool.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ImportToolsRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ImportToolsResponse;
import uz.murodjon.robotcallv2.tool.application.dto.OpenApiOperationRow;
import uz.murodjon.robotcallv2.tool.application.mapper.ToolMapper;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolImportUseCase;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.application.port.output.OpenApiDocumentPort;
import uz.murodjon.robotcallv2.tool.domain.entity.OpenApiOperation;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.service.OpenApiToolImporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Imports tools from an OpenAPI document (UNIVERSAL-OPERATOR-PLAN B.2). The document is read
 * before, and outside, the transaction the tools are saved in: fetching it can take seconds.
 * The tools are saved through {@link ToolUseCase#createTools}, so they are checked exactly as
 * hand-made ones are.
 */
@Service
public class ToolImportService implements ToolImportUseCase {

    private final OpenApiDocumentPort openApiDocumentPort;
    private final ToolUseCase toolUseCase;
    private final ToolMapper mapper;

    public ToolImportService(OpenApiDocumentPort openApiDocumentPort, ToolUseCase toolUseCase, ToolMapper mapper) {
        this.openApiDocumentPort = openApiDocumentPort;
        this.toolUseCase = toolUseCase;
        this.mapper = mapper;
    }

    @Override
    public List<OpenApiOperationRow> findOperations(ImportToolsRequest request) {
        Map<String, Object> document = readDocument(request);
        String serverUrl = OpenApiToolImporter.findServerUrl(document, request.baseUrl(), request.openApiUrl());
        return OpenApiToolImporter.readOperations(document, serverUrl != null ? serverUrl : "", request.secretPrefix())
                .stream().map(mapper::toOperationRow).toList();
    }

    @Override
    public List<Tool> findTools(ImportToolsRequest request) {
        Map<String, OpenApiOperation> byId = readImportableOperations(request);
        if (request.operationIds() == null || request.operationIds().isEmpty()) {
            return byId.values().stream().map(OpenApiOperation::tool).filter(Objects::nonNull).toList();
        }
        return new LinkedHashSet<>(request.operationIds()).stream()
                .map(operationId -> requireImportable(byId, operationId).tool())
                .toList();
    }

    @Override
    public ImportToolsResponse importTools(long companyId, ImportToolsRequest request) {
        if (request.operationIds() == null || request.operationIds().isEmpty()) {
            throw new ValidationException(ErrorCode.OPENAPI_OPERATIONS_REQUIRED);
        }
        Map<String, OpenApiOperation> byId = readImportableOperations(request);
        List<CreateToolRequest> tools = new ArrayList<>();
        TreeSet<String> secretKeys = new TreeSet<>();
        for (String operationId : new LinkedHashSet<>(request.operationIds())) {
            OpenApiOperation operation = requireImportable(byId, operationId);
            tools.add(mapper.toCreateRequest(operation.tool()));
            secretKeys.addAll(operation.secretKeys());
        }
        return new ImportToolsResponse(toolUseCase.createTools(companyId, tools), List.copyOf(secretKeys));
    }

    /** The document read once, its operations by id, with the address the tools would call resolved. */
    private Map<String, OpenApiOperation> readImportableOperations(ImportToolsRequest request) {
        Map<String, Object> document = readDocument(request);
        String serverUrl = OpenApiToolImporter.findServerUrl(document, request.baseUrl(), request.openApiUrl());
        if (serverUrl == null) {
            throw new ValidationException(ErrorCode.OPENAPI_SERVER_MISSING);
        }
        Map<String, OpenApiOperation> byId = new LinkedHashMap<>();
        OpenApiToolImporter.readOperations(document, serverUrl, request.secretPrefix())
                .forEach(operation -> byId.putIfAbsent(operation.operationId(), operation));
        return byId;
    }

    private static OpenApiOperation requireImportable(Map<String, OpenApiOperation> byId, String operationId) {
        OpenApiOperation operation = byId.get(operationId);
        if (operation == null) {
            throw new ValidationException(ErrorCode.OPENAPI_OPERATION_UNKNOWN, operationId);
        }
        if (operation.tool() == null) {
            throw new ValidationException(ErrorCode.OPENAPI_OPERATION_UNSUPPORTED, operationId,
                    operation.unsupportedReason());
        }
        return operation;
    }

    /** From exactly one of the two sources; the network is asked only for a public address. */
    private Map<String, Object> readDocument(ImportToolsRequest request) {
        boolean byUrl = request.openApiUrl() != null && !request.openApiUrl().isBlank();
        boolean byText = request.openApiSpec() != null && !request.openApiSpec().isBlank();
        if (byUrl == byText) {
            throw new ValidationException(ErrorCode.OPENAPI_SOURCE_INVALID);
        }
        Map<String, Object> document;
        if (byUrl) {
            String url = request.openApiUrl().trim();
            if (PublicUrlGuard.parsePublic(url) == null) {
                throw new ValidationException(ErrorCode.OPENAPI_URL_INVALID, url);
            }
            document = openApiDocumentPort.fetchDocument(url);
        } else {
            document = openApiDocumentPort.parseDocument(request.openApiSpec());
        }
        if (document == null) {
            throw new ValidationException(ErrorCode.OPENAPI_SPEC_UNREADABLE);
        }
        return document;
    }
}
