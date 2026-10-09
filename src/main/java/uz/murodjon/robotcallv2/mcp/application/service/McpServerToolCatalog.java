package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentEvaluationUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalMode;
import uz.murodjon.robotcallv2.mcp.domain.service.AgentDraftPolicy;
import uz.murodjon.robotcallv2.pack.application.dto.InstallDraftRequest;
import uz.murodjon.robotcallv2.pack.application.dto.PackInstallResponse;
import uz.murodjon.robotcallv2.pack.application.port.input.PackUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tools the platform's own MCP server offers (gap analysis 3.8): build an agent, test it
 * by simulation, read what its calls did, and move it in and out as a pack. Each tool is the
 * same use case the REST API calls, behind the same permission; a caller sees and may call
 * only the tools one of its permissions opens.
 *
 * <p>A caller with {@code AGENT_DRAFT_EDIT} and without {@code AI_AGENT_EDIT} — and the panel's
 * copilot, for everyone — writes drafts only ({@link AgentDraftPolicy}): the agents it creates
 * start switched off, it never changes {@code enabled}, and it may not edit an agent whose calls
 * run on the draft.
 *
 * <p>Arguments are read strictly — an unknown field is refused, so a misspelt one is not
 * silently dropped — and checked with the request's own bean-validation constraints.
 */
@Component
public class McpServerToolCatalog {

    private static final Set<Permission> AGENT_READ = EnumSet.of(Permission.AI_AGENT_READ);
    private static final Set<Permission> DRAFT_WRITE = EnumSet.of(Permission.AI_AGENT_EDIT, Permission.AGENT_DRAFT_EDIT);
    private static final Set<Permission> EVAL_START = EnumSet.of(Permission.AI_AGENT_EDIT, Permission.AGENT_DRAFT_EDIT,
            Permission.AGENT_EVAL_RUN);
    private static final Set<Permission> CALL_READ = EnumSet.of(Permission.REPORT_READ);

    private static final String AGENT_FIELDS = """
            "name": {"type": "string", "maxLength": 255},
            "description": {"type": "string", "maxLength": 500},
            "language": {"type": "string", "description": "uz-UZ, ru-RU, ..."},
            "firstMessage": {"type": "string", "description": "What the agent says when the call connects"},
            "systemPrompt": {"type": "string", "description": "The agent's instructions (scenarioMode PROMPT)"},
            "scenarioMode": {"type": "string", "enum": ["PROMPT", "STRUCTURED_STEPS"]},
            "scenarioDefinition": {"type": "object", "description": "Stages, facts and guardrails (scenarioMode STRUCTURED_STEPS); the shape export_agent returns under agent.scenario"},
            "llmModel": {"type": "string"},
            "temperature": {"type": "number"}""";

    private final AiAgentUseCase aiAgentUseCase;
    private final AgentEvaluationUseCase agentEvaluationUseCase;
    private final PackUseCase packUseCase;
    private final ReportUseCase reportUseCase;
    private final CurrentUser currentUser;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper json = newMapper();
    private final Map<String, ToolSpec> tools = new LinkedHashMap<>();

    public McpServerToolCatalog(AiAgentUseCase aiAgentUseCase, AgentEvaluationUseCase agentEvaluationUseCase,
                                PackUseCase packUseCase, ReportUseCase reportUseCase, CurrentUser currentUser,
                                Validator validator, PlatformTransactionManager transactionManager) {
        this.aiAgentUseCase = aiAgentUseCase;
        this.agentEvaluationUseCase = agentEvaluationUseCase;
        this.packUseCase = packUseCase;
        this.reportUseCase = reportUseCase;
        this.currentUser = currentUser;
        this.validator = validator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        registerTools();
    }

    /**
     * The mapper both the arguments and the answers go through: dates as ISO text, as the REST
     * API writes them. The shared bean keeps plain defaults on purpose (JacksonConfig).
     */
    static ObjectMapper newMapper() {
        return JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }

    public boolean existsTool(String name) {
        return tools.containsKey(name);
    }

    /** {@code tools/list}: the tools this caller holds a permission for, in a fixed order. */
    public List<ObjectNode> findVisibleTools() {
        List<ObjectNode> visible = new ArrayList<>();
        for (ToolSpec tool : tools.values()) {
            if (isAllowed(tool)) {
                ObjectNode descriptor = json.createObjectNode();
                descriptor.put("name", tool.name());
                descriptor.put("description", tool.description());
                descriptor.set("inputSchema", tool.inputSchema());
                visible.add(descriptor);
            }
        }
        return visible;
    }

    /**
     * {@code tools/call}: runs a known tool. Every refusal is an {@code AppException}, which the
     * server hands back as the tool's error result.
     *
     * @param forceDraftOnly treat the caller as drafts-only whatever its permissions — the panel's
     *                       copilot writes drafts only, for every user
     */
    public Object callTool(long companyId, String name, ObjectNode arguments, boolean forceDraftOnly) {
        ToolSpec tool = tools.get(name);
        if (!isAllowed(tool)) {
            throw new ForbiddenException(ErrorCode.MCP_TOOL_FORBIDDEN, name);
        }
        return tool.handler().call(companyId, name, arguments, forceDraftOnly || isDraftOnly());
    }

    private void registerTools() {
        register("list_agents", "List the company's AI agents, newest first.", AGENT_READ, """
                {"type": "object", "properties": {
                  "search": {"type": "string"},
                  "page": {"type": "integer", "minimum": 0},
                  "size": {"type": "integer", "minimum": 1, "maximum": 100}}}""",
                (companyId, tool, args, draftOnly) -> aiAgentUseCase.filterAgents(companyId, convert(tool, args, AiAgentFilter.class)));

        register("get_agent", "One agent's full settings: the draft, or a published version.", AGENT_READ, """
                {"type": "object", "required": ["agentId"], "properties": {
                  "agentId": {"type": "integer"},
                  "version": {"type": "integer", "description": "A published version; omit for the draft"}}}""",
                (companyId, tool, args, draftOnly) -> {
                    long agentId = requireId(tool, args, "agentId");
                    Integer version = optionalInt(args, "version");
                    return version == null ? aiAgentUseCase.findAgentRow(companyId, agentId)
                            : aiAgentUseCase.findVersionRow(companyId, agentId, version);
                });

        register("create_agent", """
                Create an AI agent. It is a draft: calls reach it only once it is published and attached to \
                a campaign or inbound route. Created switched off when the caller may write drafts only. \
                Other fields of the REST create request are accepted too.""", DRAFT_WRITE, """
                {"type": "object", "required": ["name"], "properties": {%s}}""".formatted(AGENT_FIELDS),
                (companyId, tool, args, draftOnly) -> {
                    ObjectNode body = args.deepCopy();
                    if (draftOnly) {
                        body.put("enabled", false);
                    }
                    return aiAgentUseCase.createAgent(companyId, convert(tool, body, CreateAiAgentRequest.class));
                });

        register("update_agent_draft", """
                Change an agent's draft; omitted fields keep their value. Live calls keep running on the \
                published version until someone publishes. Other fields of the REST update request are accepted too.""",
                DRAFT_WRITE, """
                {"type": "object", "required": ["agentId"], "properties": {"agentId": {"type": "integer"}, %s}}"""
                        .formatted(AGENT_FIELDS),
                (companyId, tool, args, draftOnly) -> {
                    long agentId = requireId(tool, args, "agentId");
                    AiAgentRow current = aiAgentUseCase.findAgentRow(companyId, agentId);
                    ObjectNode body = args.deepCopy();
                    body.remove("agentId");
                    if (draftOnly) {
                        if (!AgentDraftPolicy.isDraftEditable(current.enabled(), current.publishedVersion())) {
                            throw new ForbiddenException(ErrorCode.MCP_AGENT_LIVE_ON_DRAFT, agentId);
                        }
                        body.remove("enabled");
                    }
                    if (!body.hasNonNull("name")) {
                        body.put("name", current.name());
                    }
                    return aiAgentUseCase.updateAgent(companyId, agentId, convert(tool, body, UpdateAiAgentRequest.class));
                });

        register("list_test_cases", "An agent's evaluation test cases.", AGENT_READ, """
                {"type": "object", "required": ["agentId"], "properties": {"agentId": {"type": "integer"}}}""",
                (companyId, tool, args, draftOnly) -> agentEvaluationUseCase.findTestCases(companyId, requireId(tool, args, "agentId")));

        register("create_test_case", """
                Add an evaluation test case: a caller persona another model plays, and what the call must \
                end with. toolMocks answers the agent's company tools by name during the simulation.""",
                DRAFT_WRITE, """
                {"type": "object", "required": ["agentId", "name", "personaPrompt"], "properties": {
                  "agentId": {"type": "integer"},
                  "name": {"type": "string", "maxLength": 200},
                  "personaPrompt": {"type": "string", "maxLength": 4000},
                  "facts": {"type": "object"},
                  "language": {"type": "string"},
                  "expectDisposition": {"type": "string"},
                  "expectStage": {"type": "string"},
                  "mustSay": {"type": "array", "items": {"type": "string"}},
                  "mustNotSay": {"type": "array", "items": {"type": "string"}},
                  "maxTurns": {"type": "integer", "minimum": 1, "maximum": 30},
                  "toolMocks": {"type": "object", "additionalProperties": {"type": "string"}},
                  "expectTools": {"type": "array", "items": {"type": "string"}}}}""",
                (companyId, tool, args, draftOnly) -> {
                    long agentId = requireId(tool, args, "agentId");
                    ObjectNode body = args.deepCopy();
                    body.remove("agentId");
                    return agentEvaluationUseCase.createTestCase(companyId, agentId,
                            convert(tool, body, SaveAgentTestCaseRequest.class));
                });

        register("run_evaluation", """
                Start an evaluation: every test case is simulated against the agent (TEXT) or played as audio \
                through the call pipeline (VOICE), repeat times each. Returns at once with the run id; poll \
                get_evaluation_run until status is no longer RUNNING.""", EVAL_START, """
                {"type": "object", "required": ["agentId"], "properties": {
                  "agentId": {"type": "integer"},
                  "version": {"type": "integer", "description": "A published version; omit for the draft"},
                  "mode": {"type": "string", "enum": ["TEXT", "VOICE"]},
                  "repeat": {"type": "integer", "minimum": 1, "maximum": 10}}}""",
                (companyId, tool, args, draftOnly) -> {
                    long agentId = requireId(tool, args, "agentId");
                    AgentEvalMode mode = args.hasNonNull("mode") ? convert(tool, args.get("mode"), AgentEvalMode.class) : null;
                    Integer repeat = optionalInt(args, "repeat");
                    return agentEvaluationUseCase.evaluate(companyId, agentId, optionalInt(args, "version"), mode,
                            repeat != null ? repeat : 1);
                });

        register("get_evaluation_run", "An evaluation run: per-case pass rate, and each attempt's transcript and problems.",
                AGENT_READ, """
                {"type": "object", "required": ["agentId", "runId"], "properties": {
                  "agentId": {"type": "integer"}, "runId": {"type": "integer"}}}""",
                (companyId, tool, args, draftOnly) -> agentEvaluationUseCase.findRun(companyId, requireId(tool, args, "agentId"),
                        requireId(tool, args, "runId")));

        register("export_agent", """
                An agent as a pack (JSON): settings, scenario, tools and test cases, with the secrets its tools \
                read listed by key only. Keep it in git; import_agent_pack installs it again.""", AGENT_READ, """
                {"type": "object", "required": ["agentId"], "properties": {
                  "agentId": {"type": "integer"},
                  "version": {"type": "integer", "description": "A published version; omit for the draft"}}}""",
                (companyId, tool, args, draftOnly) -> packUseCase.exportAgent(companyId, requireId(tool, args, "agentId"),
                        optionalInt(args, "version")));

        register("import_agent_pack", """
                Install a pack (the JSON export_agent returns) as a new agent with its tools and test cases. \
                Lists the secrets the company still has to set. Created switched off when the caller may write drafts only.""",
                DRAFT_WRITE, """
                {"type": "object", "required": ["pack"], "properties": {
                  "pack": {"type": "object"},
                  "catalogUrl": {"type": "string", "description": "The company's product feed, when the pack has a catalog"},
                  "catalogItemsPath": {"type": "string"}}}""",
                (companyId, tool, args, draftOnly) -> importPack(companyId, convert(tool, args, InstallDraftRequest.class), draftOnly));

        register("list_calls", "The company's calls, newest first, with disposition and summary.", CALL_READ, """
                {"type": "object", "properties": {
                  "q": {"type": "string", "description": "Phone number or text"},
                  "campaignId": {"type": "integer"},
                  "disposition": {"type": "string"},
                  "dateFrom": {"type": "string", "format": "date-time"},
                  "dateTo": {"type": "string", "format": "date-time"},
                  "page": {"type": "integer", "minimum": 0},
                  "size": {"type": "integer", "minimum": 1, "maximum": 100}}}""",
                (companyId, tool, args, draftOnly) -> reportUseCase.findCalls(companyId, convert(tool, args, CallFilter.class)));

        register("get_call", "One call in full: transcript, extracted data, quality verdict.", CALL_READ, """
                {"type": "object", "required": ["callId"], "properties": {"callId": {"type": "integer"}}}""",
                (companyId, tool, args, draftOnly) -> reportUseCase.findCallDetail(companyId, requireId(tool, args, "callId")));
    }

    /** A drafts-only caller's agent is switched off in the same transaction it is created in. */
    private PackInstallResponse importPack(long companyId, InstallDraftRequest request, boolean draftOnly) {
        if (!draftOnly) {
            return packUseCase.installDraft(companyId, request);
        }
        return transactionTemplate.execute(status -> {
            PackInstallResponse installed = packUseCase.installDraft(companyId, request);
            ObjectNode switchOff = json.createObjectNode()
                    .put("name", aiAgentUseCase.findAgentRow(companyId, installed.agentId()).name())
                    .put("enabled", false);
            aiAgentUseCase.updateAgent(companyId, installed.agentId(),
                    convert("import_agent_pack", switchOff, UpdateAiAgentRequest.class));
            return installed;
        });
    }

    private void register(String name, String description, Set<Permission> anyOf, String inputSchema, Handler handler) {
        try {
            tools.put(name, new ToolSpec(name, description, anyOf, json.readTree(inputSchema), handler));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("MCP tool " + name + " has an invalid input schema", e);
        }
    }

    private boolean isAllowed(ToolSpec tool) {
        return tool != null && tool.anyOf().stream().anyMatch(currentUser::hasPermission);
    }

    private boolean isDraftOnly() {
        return !currentUser.hasPermission(Permission.AI_AGENT_EDIT);
    }

    private <T> T convert(String tool, JsonNode args, Class<T> type) {
        T value;
        try {
            value = json.treeToValue(args, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            String reason = e instanceof JsonProcessingException parse ? parse.getOriginalMessage() : e.getMessage();
            throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, List.of(reason), tool);
        }
        List<String> problems = validator.validate(value).stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .toList();
        if (!problems.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, problems, tool);
        }
        return value;
    }

    /** A model writes an id as a number or as digits in a string; both are taken. */
    private static long requireId(String tool, JsonNode args, String field) {
        JsonNode value = args.get(field);
        if (value != null && value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value != null && value.isTextual() && value.asText().matches("\\d{1,18}")) {
            return Long.parseLong(value.asText());
        }
        throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, List.of(field + " is required"), tool);
    }

    private static Integer optionalInt(JsonNode args, String field) {
        JsonNode value = args.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    @FunctionalInterface
    private interface Handler {
        Object call(long companyId, String tool, ObjectNode arguments, boolean draftOnly);
    }

    private record ToolSpec(String name, String description, Set<Permission> anyOf, JsonNode inputSchema,
                            Handler handler) {
    }
}
