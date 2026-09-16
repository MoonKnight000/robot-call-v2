package uz.murodjon.robotcallv2.pack.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentEvaluationUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.conversion.application.port.input.ConversionUseCase;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeSourceUseCase;
import uz.murodjon.robotcallv2.pack.application.dto.GeneratePackRequest;
import uz.murodjon.robotcallv2.pack.application.dto.InstallDraftRequest;
import uz.murodjon.robotcallv2.pack.application.dto.InstallPackRequest;
import uz.murodjon.robotcallv2.pack.application.dto.PackInstallResponse;
import uz.murodjon.robotcallv2.pack.application.dto.PackRow;
import uz.murodjon.robotcallv2.pack.application.mapper.PackMapper;
import uz.murodjon.robotcallv2.pack.application.port.input.PackUseCase;
import uz.murodjon.robotcallv2.pack.application.port.output.PackCatalogPort;
import uz.murodjon.robotcallv2.pack.application.port.output.PackGeneratorPort;
import uz.murodjon.robotcallv2.pack.domain.entity.PackCatalogFeed;
import uz.murodjon.robotcallv2.pack.domain.entity.PackSecret;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.pack.domain.service.PackSecrets;
import uz.murodjon.robotcallv2.scenario.domain.service.ScenarioValidator;
import uz.murodjon.robotcallv2.secret.application.dto.SecretRow;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.application.dto.ImportToolsRequest;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolImportUseCase;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Installs an industry pack through the use cases of the features that own its parts, so a
 * pack's agent, tools and test cases are checked exactly as ones made by hand are.
 */
@Service
public class PackService implements PackUseCase {

    private final PackCatalogPort packCatalog;
    private final PackMapper mapper;
    private final AiAgentUseCase aiAgentUseCase;
    private final ToolUseCase toolUseCase;
    private final KnowledgeSourceUseCase knowledgeSourceUseCase;
    private final AgentEvaluationUseCase agentEvaluationUseCase;
    private final ConversionUseCase conversionUseCase;
    private final SecretUseCase secretUseCase;
    private final AuditUseCase auditUseCase;
    /** E.1: the tools of a generated pack come from an API document, never from the model. */
    private final ToolImportUseCase toolImportUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final PackGeneratorPort packGenerator;

    public PackService(PackCatalogPort packCatalog,
                       PackMapper mapper,
                       AiAgentUseCase aiAgentUseCase,
                       ToolUseCase toolUseCase,
                       KnowledgeSourceUseCase knowledgeSourceUseCase,
                       AgentEvaluationUseCase agentEvaluationUseCase,
                       ConversionUseCase conversionUseCase,
                       SecretUseCase secretUseCase,
                       AuditUseCase auditUseCase,
                       ToolImportUseCase toolImportUseCase,
                       CompanyConfigUseCase companyConfigUseCase,
                       PackGeneratorPort packGenerator) {
        this.packCatalog = packCatalog;
        this.mapper = mapper;
        this.aiAgentUseCase = aiAgentUseCase;
        this.toolUseCase = toolUseCase;
        this.knowledgeSourceUseCase = knowledgeSourceUseCase;
        this.agentEvaluationUseCase = agentEvaluationUseCase;
        this.conversionUseCase = conversionUseCase;
        this.secretUseCase = secretUseCase;
        this.auditUseCase = auditUseCase;
        this.toolImportUseCase = toolImportUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.packGenerator = packGenerator;
    }

    @Override
    public List<PackRow> findPacks() {
        return packCatalog.findAll().stream().map(mapper::toPackRow).toList();
    }

    /**
     * One transaction: a half-installed pack — an agent without its order tool — would look
     * ready and could not take an order, so a failure anywhere leaves nothing behind. The
     * catalog's first fetch is queued only once this commits.
     */
    @Override
    @Transactional
    public PackInstallResponse installPack(long companyId, String key, InstallPackRequest request) {
        VerticalPack pack = packCatalog.findByKey(key);
        if (pack == null) {
            throw new NotFoundException(ErrorCode.PACK_NOT_FOUND, key);
        }
        // A shipped pack is ours, but it is checked like a draft all the same: a stage that
        // cannot reach END_CALL is a defect worth a clear error rather than a strange agent.
        requireInstallable(pack);
        return install(companyId, pack, request);
    }

    /**
     * The tools come from the API document and the conversation from the model, and neither
     * writes the other's half: an endpoint the model invented would be a request to an address
     * nobody checked, and a scenario the importer invented would be a call nobody scripted.
     */
    @Override
    public VerticalPack generatePack(long companyId, GeneratePackRequest request) {
        List<Tool> tools = request.hasApi()
                ? toolImportUseCase.findTools(new ImportToolsRequest(request.openApiUrl(), request.openApiSpec(),
                        null, request.baseUrl(), request.secretPrefix()))
                : List.of();
        String language = companyConfigUseCase.resolveLanguage(companyId, request.language());
        VerticalPack draft = packGenerator.generate(request.business(), language, tools);
        VerticalPack complete = new VerticalPack(draft.key(), draft.name(), draft.description(), draft.industry(),
                draft.agent(), tools, draft.catalogFeed(), draft.conversionGoal(), draft.testCases(),
                PackSecrets.of(tools));
        requireInstallable(complete);
        auditUseCase.record(companyId, "PACK_GENERATE", "pack", complete.key(),
                tools.size() + " tool(s), " + complete.testCases().size() + " test case(s)");
        return complete;
    }

    @Override
    @Transactional
    public PackInstallResponse installDraft(long companyId, InstallDraftRequest request) {
        requireInstallable(request.pack());
        return install(companyId, request.pack(),
                new InstallPackRequest(request.catalogUrl(), request.catalogItemsPath()));
    }

    /**
     * What a person is shown has to be something the install will accept, and a draft arrives
     * from a model or from a client — neither of which the rest of this class should have to
     * distrust twice. The agent, the tools and the test cases are checked by the features that
     * store them; the scenario is the one part nothing else checks (a pack's scenario is stored
     * on the agent rather than as a scenario row), so it is checked here.
     */
    private static void requireInstallable(VerticalPack pack) {
        if (pack == null || pack.agent() == null || pack.agent().scenario() == null) {
            throw new ValidationException(ErrorCode.PACK_DRAFT_INVALID, 1);
        }
        List<String> problems = ScenarioValidator.validate(pack.agent().scenario());
        if (!problems.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.PACK_DRAFT_INVALID, problems, problems.size());
        }
    }

    private PackInstallResponse install(long companyId, VerticalPack pack, InstallPackRequest request) {
        long agentId = aiAgentUseCase.createAgent(companyId, mapper.toCreateAiAgentRequest(pack.agent())).id();
        List<Long> toolIds = new ArrayList<>();
        for (Tool tool : pack.tools()) {
            Long toolId = toolUseCase.createTool(companyId, mapper.toCreateToolRequest(tool)).id();
            toolUseCase.attachTool(companyId, agentId, toolId);
            toolIds.add(toolId);
        }
        Long catalogSourceId = createCatalogSource(companyId, agentId, pack.catalogFeed(), request);
        List<Long> testCaseIds = pack.testCases().stream()
                .map(testCase -> agentEvaluationUseCase.createTestCase(companyId, agentId,
                        mapper.toSaveAgentTestCaseRequest(testCase)).id())
                .toList();
        String conversionGoalKey = createConversionGoalIfAbsent(companyId, pack.conversionGoal());
        auditUseCase.record(companyId, "PACK_INSTALL", "pack", pack.key(), "agent " + agentId);
        return new PackInstallResponse(agentId, toolIds, catalogSourceId, testCaseIds, conversionGoalKey,
                findMissingSecrets(companyId, pack.secrets()));
    }

    private Long createCatalogSource(long companyId, long agentId, PackCatalogFeed feed, InstallPackRequest request) {
        if (feed == null || request == null || request.catalogUrl() == null || request.catalogUrl().isBlank()) {
            return null;
        }
        return knowledgeSourceUseCase.createKnowledgeSources(companyId,
                mapper.toCreateKnowledgeSourceRequest(agentId, feed, request)).getFirst().id();
    }

    /** Never over an existing goal: its window is the company's, and other agents' calls are counted against it. */
    private String createConversionGoalIfAbsent(long companyId, ConversionGoal goal) {
        if (goal == null || conversionUseCase.findGoals(companyId).stream()
                .anyMatch(existing -> existing.goalKey().equals(goal.goalKey()))) {
            return null;
        }
        return conversionUseCase.upsertGoal(companyId, mapper.toConversionGoalRequest(goal)).goalKey();
    }

    private List<PackSecret> findMissingSecrets(long companyId, List<PackSecret> secrets) {
        Set<String> existing = secretUseCase.findSecrets(companyId).stream()
                .map(SecretRow::key)
                .collect(Collectors.toSet());
        return secrets.stream().filter(secret -> !existing.contains(secret.key())).toList();
    }
}
