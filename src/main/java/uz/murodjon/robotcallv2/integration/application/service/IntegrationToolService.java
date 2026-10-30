package uz.murodjon.robotcallv2.integration.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmGrant;
import uz.murodjon.robotcallv2.integration.application.dto.CrmToolRow;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationToolAdminRow;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateCrmToolsRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmAccountProbePort;
import uz.murodjon.robotcallv2.integration.application.port.output.IntegrationToolRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmTool;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationToolKind;
import uz.murodjon.robotcallv2.integration.domain.enums.ToolPropagation;
import uz.murodjon.robotcallv2.integration.domain.service.IntegrationToolValidator;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.TestToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolTestResponse;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The tools of the integration a company uses (Uysot): the catalog the platform admin keeps and
 * each company's own choice from it.
 *
 * <p>A company's choice is stored only where it differs from the tool's default, so a company
 * that never chose keeps following the default, and a company that chose is never overwritten
 * by a default change.
 */
@Service
public class IntegrationToolService implements IntegrationToolUseCase {

    private static final CrmProvider PROVIDER = CrmProvider.UYSOT;
    private static final String TOKEN_HEADER = "X-Open-Api-Token";

    private final IntegrationToolRepository repository;
    private final CrmIntegrationUseCase crmIntegrationUseCase;
    private final CompanyUseCase companyUseCase;
    private final AuditUseCase audit;
    private final ToolUseCase toolUseCase;
    private final CrmAccountProbePort crmAccountProbePort;

    public IntegrationToolService(IntegrationToolRepository repository, CrmIntegrationUseCase crmIntegrationUseCase,
                                  CompanyUseCase companyUseCase, AuditUseCase audit, ToolUseCase toolUseCase,
                                  CrmAccountProbePort crmAccountProbePort) {
        this.repository = repository;
        this.crmIntegrationUseCase = crmIntegrationUseCase;
        this.companyUseCase = companyUseCase;
        this.audit = audit;
        this.toolUseCase = toolUseCase;
        this.crmAccountProbePort = crmAccountProbePort;
    }

    @Override
    public List<CrmToolRow> findTools(long companyId) {
        Map<Long, Boolean> choices = repository.findChoices(companyId);
        List<CrmGrant> grants = crmIntegrationUseCase.findGrants(companyId);
        return findOfferedTools().stream()
                .map(tool -> CrmToolRow.of(tool, tool.isEnabledFor(choices.get(tool.id())),
                        !choices.containsKey(tool.id()), isGranted(tool, grants)))
                .toList();
    }

    @Override
    public List<CrmToolRow> updateTools(long companyId, UpdateCrmToolsRequest request) {
        companyUseCase.requireIntegrationEnabled(companyId, IntegrationProvider.UYSOT);
        List<IntegrationTool> offered = findOfferedTools();
        Set<String> wanted = new HashSet<>(request.enabledTools());
        Set<String> known = offered.stream().map(IntegrationTool::key).collect(Collectors.toSet());
        wanted.stream().filter(key -> !known.contains(key)).findFirst().ifPresent(key -> {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_UNKNOWN, key);
        });

        Map<Long, Boolean> choices = repository.findChoices(companyId);
        for (IntegrationTool tool : offered) {
            boolean enabled = wanted.contains(tool.key());
            // A choice equal to the default is not stored: the company keeps following the default.
            Boolean desired = enabled == tool.defaultEnabled() ? null : enabled;
            Boolean current = choices.get(tool.id());
            if (Objects.equals(desired, current)) {
                continue;
            }
            if (desired == null) {
                repository.deleteChoice(companyId, tool.id());
            } else {
                repository.saveChoice(companyId, tool.id(), desired);
            }
        }
        audit.record(companyId, "CRM_INTEGRATION_TOOLS", "crm_integration", String.valueOf(companyId),
                offered.stream().filter(tool -> !wanted.contains(tool.key())).map(IntegrationTool::key).sorted()
                        .collect(Collectors.collectingAndThen(Collectors.joining(","),
                                off -> off.isEmpty() ? "all on" : "off: " + off)));
        return findTools(companyId);
    }

    @Override
    public Set<String> findEnabledToolNames(long companyId) {
        return findEnabledTools(companyId, IntegrationToolKind.BUILT_IN).stream()
                .flatMap(tool -> CrmTool.findByKey(tool.key()).stream())
                .map(CrmTool::toolName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<Tool> findEnabledHttpTools(long companyId) {
        List<IntegrationTool> enabled = findEnabledTools(companyId, IntegrationToolKind.HTTP);
        if (enabled.isEmpty()) {
            return List.of();
        }
        CrmConnection connection = findUysotConnection(companyId);
        if (connection == null) {
            return List.of();
        }
        return enabled.stream()
                .map(IntegrationTool::definition)
                .filter(Objects::nonNull)
                .map(template -> bind(template, connection))
                .toList();
    }

    /** No transaction on purpose: the request goes out over the network. */
    @Override
    public ToolTestResponse runEnabledReadTool(long companyId, String toolName, Map<String, Object> arguments) {
        Tool tool = findEnabledHttpTools(companyId).stream()
                .filter(candidate -> candidate.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.TARGET_SOURCE_TOOL_UNAVAILABLE, toolName));
        if (tool.effect() != ToolEffect.READ) {
            throw new ValidationException(ErrorCode.TARGET_SOURCE_TOOL_NOT_READ_ONLY, toolName);
        }
        return toolUseCase.testResolvedTool(tool, new TestToolRequest(arguments, Map.of("company_id", companyId), null));
    }

    @Override
    public Map<String, String> findBuiltInModelDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        for (IntegrationTool tool : findOfferedTools()) {
            if (tool.kind() != IntegrationToolKind.BUILT_IN || tool.modelDescription() == null) {
                continue;
            }
            CrmTool.findByKey(tool.key()).ifPresent(handler -> descriptions.put(handler.toolName(), tool.modelDescription()));
        }
        return descriptions;
    }

    /** No transaction on purpose: the request goes out over the network. */
    @Override
    public ToolTestResponse testCatalogTool(long adminCompanyId, long id, TestToolRequest request) {
        IntegrationTool tool = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INTEGRATION_TOOL_NOT_FOUND, id));
        if (tool.definition() == null) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_BUILT_IN_LOCKED);
        }
        CrmConnection connection = findUysotConnection(adminCompanyId);
        if (connection == null) {
            throw new ConflictException(ErrorCode.INTEGRATION_TOOL_TEST_NOT_CONNECTED);
        }
        audit.record(adminCompanyId, "INTEGRATION_TOOL_TEST", "integration_tool", String.valueOf(id), tool.key());
        return toolUseCase.testResolvedTool(bind(tool.definition(), connection), request);
    }

    /**
     * The company's Uysot token at the platform's Uysot address. The connection use case leaves
     * the address out for Uysot (it is platform configuration), so it is added here.
     */
    private CrmConnection findUysotConnection(long companyId) {
        String baseUrl = crmAccountProbePort.findUysotBaseUrl();
        if (baseUrl == null) {
            return null;
        }
        return crmIntegrationUseCase.findConnection(companyId)
                .filter(c -> c.provider() == PROVIDER && c.accessToken() != null)
                .map(c -> new CrmConnection(PROVIDER, baseUrl, c.accessToken()))
                .orElse(null);
    }

    /**
     * The template as a tool that calls this company's account: the relative path joined to its
     * Uysot address, its token in the one header Uysot reads. The model can fill only the path's
     * own placeholders, which the executor URL-encodes, so it cannot leave the host.
     */
    static Tool bind(Tool template, CrmConnection connection) {
        ToolHttpConfig http = template.http();
        String base = connection.baseUrl().endsWith("/")
                ? connection.baseUrl().substring(0, connection.baseUrl().length() - 1)
                : connection.baseUrl();
        List<ToolKeyValuePair> headers = List.of(
                new ToolKeyValuePair(TOKEN_HEADER, connection.accessToken(), null, true),
                new ToolKeyValuePair("Content-Type", "application/json", null, null));
        return new Tool(null, null, template.name(), template.description(),
                new ToolHttpConfig(base + "/" + http.url(), http.method(), headers, http.body(), http.queryParams(),
                        http.pathParams(), http.bodySchema(), http.responseTimeoutSecs(), http.retryAttempts(), null,
                        http.waitForResult(), http.statusVariable()),
                template.dynamicVariables(), template.speech(), template.responseMapping(), template.maxCallsPerCall(),
                template.effect(), template.requiresConfirmation(), template.readbackTemplate(),
                template.createdAt(), template.updatedAt());
    }

    @Override
    public List<IntegrationToolAdminRow> findCatalog(CrmProvider provider) {
        return repository.findByProvider(provider).stream().map(this::toAdminRow).toList();
    }

    @Override
    @Transactional
    public IntegrationToolAdminRow createCatalogTool(long adminCompanyId, CreateIntegrationToolRequest request) {
        IntegrationToolValidator.validateKey(request.key());
        List<IntegrationTool> existing = repository.findByProvider(request.provider());
        if (existing.stream().anyMatch(tool -> tool.key().equals(request.key()))) {
            throw new ConflictException(ErrorCode.INTEGRATION_TOOL_KEY_TAKEN, request.key());
        }
        requireGrantComplete(request.permission(), request.scope());
        boolean startsEnabled = request.defaultEnabled();
        if (startsEnabled && request.applyTo() == null) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_PROPAGATION_REQUIRED);
        }
        Tool template = buildTemplate(request.definition(), existing, null);
        IntegrationTool saved = repository.create(IntegrationTool.builder()
                .provider(request.provider())
                .key(request.key())
                .kind(IntegrationToolKind.HTTP)
                .group(request.group())
                .label(request.label().trim())
                .description(request.description().trim())
                .permission(request.permission())
                .scope(request.scope())
                .defaultEnabled(startsEnabled)
                .platformEnabled(request.platformEnabled())
                .sortOrder(request.sortOrder())
                .definition(template)
                .build());
        // A new tool has no past: "new only" means companies that exist now do not get it.
        int pinned = startsEnabled && request.applyTo() == ToolPropagation.NEW_ONLY
                ? repository.pinCompaniesWithoutChoice(saved.id(), false)
                : 0;
        audit.record(adminCompanyId, "INTEGRATION_TOOL_CREATE", "integration_tool", String.valueOf(saved.id()),
                saved.key() + " default=" + startsEnabled + (startsEnabled ? " applyTo=" + request.applyTo() + " pinned=" + pinned : ""));
        return toAdminRow(saved);
    }

    @Override
    @Transactional
    public IntegrationToolAdminRow updateCatalogTool(long adminCompanyId, long id, UpdateIntegrationToolRequest request) {
        IntegrationTool current = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INTEGRATION_TOOL_NOT_FOUND, id));
        boolean defaultChanged = request.defaultEnabled() != current.defaultEnabled();
        if (defaultChanged && request.applyTo() == null) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_PROPAGATION_REQUIRED);
        }
        IntegrationTool edited = current.toBuilder()
                .label(request.label().trim())
                .description(request.description().trim())
                .group(request.group())
                .defaultEnabled(request.defaultEnabled())
                .platformEnabled(request.platformEnabled())
                .sortOrder(request.sortOrder())
                .build();
        if (current.kind() == IntegrationToolKind.BUILT_IN) {
            edited = edited.toBuilder()
                    .modelDescription(request.modelDescription() == null || request.modelDescription().isBlank()
                            ? null : request.modelDescription().trim())
                    .build();
        }
        if (current.kind() == IntegrationToolKind.HTTP) {
            if (request.definition() == null) {
                throw new ValidationException(ErrorCode.INTEGRATION_TOOL_DEFINITION_REQUIRED);
            }
            requireGrantComplete(request.permission(), request.scope());
            edited = edited.toBuilder()
                    .permission(request.permission())
                    .scope(request.scope())
                    .definition(buildTemplate(request.definition(), repository.findByProvider(current.provider()), current))
                    .build();
        }
        // Pin first: it reads "no choice yet" and must see the tool as it was, before the default moves.
        int pinned = defaultChanged && request.applyTo() == ToolPropagation.NEW_ONLY
                ? repository.pinCompaniesWithoutChoice(id, current.defaultEnabled())
                : 0;
        IntegrationTool saved = repository.update(edited);
        audit.record(adminCompanyId, "INTEGRATION_TOOL_UPDATE", "integration_tool", String.valueOf(id),
                current.key() + (defaultChanged
                        ? " default " + current.defaultEnabled() + "->" + saved.defaultEnabled()
                                + " applyTo=" + request.applyTo() + " pinned=" + pinned
                        : ""));
        return toAdminRow(saved);
    }

    @Override
    @Transactional
    public void deleteCatalogTool(long adminCompanyId, long id) {
        IntegrationTool tool = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INTEGRATION_TOOL_NOT_FOUND, id));
        if (tool.kind() != IntegrationToolKind.HTTP) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_BUILT_IN_LOCKED);
        }
        repository.deleteById(id);
        audit.record(adminCompanyId, "INTEGRATION_TOOL_DELETE", "integration_tool", String.valueOf(id), tool.key());
    }

    /** What a company is shown: the tools the platform has not switched off. */
    private List<IntegrationTool> findOfferedTools() {
        return repository.findByProvider(PROVIDER).stream().filter(IntegrationTool::platformEnabled).toList();
    }

    /** The tools of one kind the company's agent may be offered: on, offered by the platform, covered by its grants. */
    private List<IntegrationTool> findEnabledTools(long companyId, IntegrationToolKind kind) {
        Map<Long, Boolean> choices = repository.findChoices(companyId);
        List<CrmGrant> grants = crmIntegrationUseCase.findGrants(companyId);
        return findOfferedTools().stream()
                .filter(tool -> tool.kind() == kind)
                .filter(tool -> tool.isEnabledFor(choices.get(tool.id())) && isGranted(tool, grants))
                .toList();
    }

    private IntegrationToolAdminRow toAdminRow(IntegrationTool tool) {
        CreateToolRequest definition = tool.definition() != null ? toolUseCase.toTemplateRequest(tool.definition()) : null;
        return IntegrationToolAdminRow.of(tool, definition);
    }

    /**
     * Checks and builds an HTTP tool's template. {@code editing} is the tool being changed, whose
     * own model-facing name is not "taken".
     */
    private Tool buildTemplate(CreateToolRequest request, List<IntegrationTool> catalog, IntegrationTool editing) {
        Tool template = toolUseCase.buildTemplate(request);
        IntegrationToolValidator.validateRequest(template);
        Set<String> taken = Arrays.stream(CrmTool.values()).map(CrmTool::toolName).collect(Collectors.toCollection(HashSet::new));
        catalog.stream()
                .filter(tool -> tool.definition() != null && (editing == null || tool.id() != editing.id()))
                .forEach(tool -> taken.add(tool.definition().name()));
        IntegrationToolValidator.validateModelName(template.name(), taken);
        return template;
    }

    private static void requireGrantComplete(CrmPermission permission, CrmScopeLevel scope) {
        if ((permission == null) != (scope == null)) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_GRANT_INCOMPLETE);
        }
    }

    /**
     * Whether the company's grants cover the tool. No grants on record means the company runs on
     * the platform's own token (no consent screen), so there is nothing to check against and the
     * tool is not held back.
     */
    private static boolean isGranted(IntegrationTool tool, List<CrmGrant> grants) {
        return tool.permission() == null || grants.isEmpty()
                || grants.stream().anyMatch(g -> g.permission() == tool.permission() && g.scope() == tool.scope());
    }
}
