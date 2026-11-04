package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeBaseUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeSourceUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.mcp.domain.service.AgentDraftDiff;
import uz.murodjon.robotcallv2.mcp.domain.service.AgentDraftPolicy;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything an agent is assembled from besides its own settings: the voices and models it may
 * pick, the knowledge it answers from, the company tools it calls, the inbound lines it sits on —
 * and what publishing its draft would change.
 *
 * <p>Tool links and knowledge are read live by calls, not from a published snapshot, so a
 * drafts-only caller (the copilot always) attaches them only to an agent that takes no calls yet
 * ({@link AgentDraftPolicy#isOffline}); a company-wide knowledge item is out of its reach.
 */
@Component
@Order(40)
public class AgentResourcesMcpToolSet implements McpToolSet {

    private static final Set<Permission> AGENT_READ = EnumSet.of(Permission.AI_AGENT_READ);
    private static final Set<Permission> AGENT_WRITE = EnumSet.of(Permission.AI_AGENT_EDIT, Permission.AGENT_DRAFT_EDIT);
    private static final Set<Permission> KNOWLEDGE_READ = EnumSet.of(Permission.KNOWLEDGE_BASE_READ, Permission.KNOWLEDGE_BASE_EDIT);
    private static final Set<Permission> KNOWLEDGE_WRITE = EnumSet.of(Permission.KNOWLEDGE_BASE_EDIT);
    private static final Set<Permission> INBOUND_READ = EnumSet.of(Permission.INBOUND_ROUTE_READ, Permission.INBOUND_ROUTE_EDIT);

    private static final String MODE = """
            "mode": {"type": "string", "enum": ["CASCADE", "REALTIME", "HALF_CASCADE"], "description": "The agent's pipeline; omit for CASCADE"}""";

    private final AiAgentUseCase aiAgentUseCase;
    private final TtsVoiceUseCase voices;
    private final AiModelUseCase aiModelUseCase;
    private final KnowledgeBaseUseCase knowledgeBaseUseCase;
    private final KnowledgeSourceUseCase knowledgeSourceUseCase;
    private final ToolUseCase toolUseCase;
    private final InboundRouteUseCase inboundRouteUseCase;
    private final McpToolArguments arguments;

    public AgentResourcesMcpToolSet(AiAgentUseCase aiAgentUseCase, TtsVoiceUseCase voices,
                                    AiModelUseCase aiModelUseCase, KnowledgeBaseUseCase knowledgeBaseUseCase,
                                    KnowledgeSourceUseCase knowledgeSourceUseCase, ToolUseCase toolUseCase,
                                    InboundRouteUseCase inboundRouteUseCase, Validator validator) {
        this.aiAgentUseCase = aiAgentUseCase;
        this.voices = voices;
        this.aiModelUseCase = aiModelUseCase;
        this.knowledgeBaseUseCase = knowledgeBaseUseCase;
        this.knowledgeSourceUseCase = knowledgeSourceUseCase;
        this.toolUseCase = toolUseCase;
        this.inboundRouteUseCase = inboundRouteUseCase;
        this.arguments = new McpToolArguments(validator);
    }

    @Override
    public List<McpToolDefinition> findTools() {
        return List.of(
                new McpToolDefinition("list_voices", """
                        The voices this company may give an agent (id, name, language, gender, provider). \
                        Put the id in update_agent_draft as voice.voiceId.""", AGENT_READ, """
                        {"type": "object", "properties": {%s,
                          "language": {"type": "string", "description": "uz-UZ, ru-RU ...; omit for all"}}}""".formatted(MODE),
                        (companyId, tool, args, draftOnly) -> voices.findSelectableByCompanyIdAndMode(companyId,
                                mode(tool, args), arguments.optionalText(args, "language"))),

                new McpToolDefinition("list_llm_models", """
                        The language models an agent may use (llmModel / fastLlmModel), with their prices.""",
                        AGENT_READ, """
                        {"type": "object", "properties": {%s}}""".formatted(MODE),
                        (companyId, tool, args, draftOnly) -> aiModelUseCase.findPricedSelectableByKindAndMode(AiModelKind.LLM,
                                mode(tool, args))),

                new McpToolDefinition("diff_agent_draft", """
                        What publishing would change: every field where the draft differs from the published \
                        version ({field: {published, draft}}). Empty when there is nothing to publish; \
                        neverPublished when the agent has no published version yet.""", AGENT_READ, """
                        {"type": "object", "required": ["agentId"], "properties": {"agentId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> diffDraft(companyId, arguments.requireId(tool, args, "agentId"))),

                new McpToolDefinition("list_knowledge", """
                        The knowledge an agent answers from: question-answer items (agentId, or company-wide \
                        when omitted) and uploaded sources (files, sites) with their indexing status.""",
                        KNOWLEDGE_READ, """
                        {"type": "object", "properties": {
                          "agentId": {"type": "integer"},
                          "search": {"type": "string"},
                          "page": {"type": "integer", "minimum": 0},
                          "size": {"type": "integer", "minimum": 1, "maximum": 100}}}""",
                        (companyId, tool, args, draftOnly) -> {
                            Long agentId = arguments.optionalId(tool, args, "agentId");
                            Map<String, Object> answer = new LinkedHashMap<>();
                            answer.put("items", knowledgeBaseUseCase.list(companyId, arguments.convert(tool, args, KnowledgeItemFilter.class)));
                            answer.put("sources", knowledgeSourceUseCase.findKnowledgeSources(companyId, agentId));
                            return answer;
                        }),

                new McpToolDefinition("add_knowledge_item", """
                        Add a question-answer item to an agent's knowledge (answers in Uzbek, and Russian / \
                        English when known). Facts only from the user — never invent a price or a term.""",
                        KNOWLEDGE_WRITE, """
                        {"type": "object", "required": ["agentId", "key", "topic", "title", "answerUz"], "properties": {
                          "agentId": {"type": "integer"},
                          "key": {"type": "string", "maxLength": 100, "description": "snake_case, unique"},
                          "topic": {"type": "string", "maxLength": 50},
                          "title": {"type": "string", "maxLength": 255, "description": "The question as a caller asks it"},
                          "answerUz": {"type": "string"},
                          "answerRu": {"type": "string"},
                          "answerEn": {"type": "string"},
                          "keywords": {"type": "string"}}}""",
                        (companyId, tool, args, draftOnly) -> {
                            requireOfflineWhenDraftOnly(companyId, arguments.requireId(tool, args, "agentId"), draftOnly);
                            return knowledgeBaseUseCase.create(companyId, arguments.convert(tool, args, KnowledgeItemCreateRequest.class));
                        }),

                new McpToolDefinition("list_tools", """
                        The company's REST tools (functions an agent can call mid-conversation), or with \
                        agentId only the ones attached to that agent.""", AGENT_READ, """
                        {"type": "object", "properties": {"agentId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> {
                            Long agentId = arguments.optionalId(tool, args, "agentId");
                            return agentId != null ? toolUseCase.findAgentTools(companyId, agentId) : toolUseCase.findTools(companyId);
                        }),

                new McpToolDefinition("create_rest_tool", """
                        Create a company REST tool (not attached to any agent yet). Secrets go in as \
                        {{secret.KEY}} references only — never write a key's value.""", AGENT_WRITE, """
                        {"type": "object", "required": ["name", "description", "http"], "properties": {
                          "name": {"type": "string", "maxLength": 100},
                          "description": {"type": "string", "maxLength": 500, "description": "When the agent should call it"},
                          "http": {"type": "object", "description": "method, url, headers, query, body"},
                          "responseMapping": {"type": "object"},
                          "speech": {"type": "object"},
                          "requiresConfirmation": {"type": "boolean"}}}""",
                        (companyId, tool, args, draftOnly) -> toolUseCase.createTool(companyId,
                                arguments.convert(tool, args, CreateToolRequest.class))),

                new McpToolDefinition("attach_tool_to_agent", """
                        Let an agent call a company tool. Drafts-only callers: only an agent that takes no \
                        calls yet (switched off, never published).""", AGENT_WRITE, """
                        {"type": "object", "required": ["agentId", "toolId"], "properties": {
                          "agentId": {"type": "integer"}, "toolId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> {
                            long agentId = arguments.requireId(tool, args, "agentId");
                            requireOfflineWhenDraftOnly(companyId, agentId, draftOnly);
                            toolUseCase.attachTool(companyId, agentId, arguments.requireId(tool, args, "toolId"));
                            return toolUseCase.findAgentTools(companyId, agentId);
                        }),

                new McpToolDefinition("list_inbound_routes", """
                        The company's inbound numbers and which agent or call flow answers each.""",
                        INBOUND_READ, """
                        {"type": "object", "properties": {
                          "page": {"type": "integer", "minimum": 0},
                          "size": {"type": "integer", "minimum": 1, "maximum": 100}}}""",
                        (companyId, tool, args, draftOnly) -> inboundRouteUseCase.list(companyId,
                                arguments.convert(tool, args, InboundRouteFilter.class))));
    }

    private ObjectNode diffDraft(long companyId, long agentId) {
        AiAgentRow draft = aiAgentUseCase.findAgentRow(companyId, agentId);
        ObjectNode answer = arguments.mapper().createObjectNode();
        if (draft.publishedVersion() == null) {
            answer.put("neverPublished", true);
            return answer;
        }
        AiAgentRow published = aiAgentUseCase.findVersionRow(companyId, agentId, draft.publishedVersion());
        answer.put("publishedVersion", draft.publishedVersion());
        answer.set("changes", AgentDraftDiff.diff(arguments.mapper().valueToTree(published),
                arguments.mapper().valueToTree(draft)));
        return answer;
    }

    private void requireOfflineWhenDraftOnly(long companyId, long agentId, boolean draftOnly) {
        AiAgentRow agent = aiAgentUseCase.findAgentRow(companyId, agentId);
        if (draftOnly && !AgentDraftPolicy.isOffline(agent.enabled(), agent.publishedVersion())) {
            throw new ForbiddenException(ErrorCode.MCP_AGENT_NOT_OFFLINE, agentId);
        }
    }

    private PipelineMode mode(String tool, ObjectNode args) {
        return args.hasNonNull("mode") ? arguments.convert(tool, args.get("mode"), PipelineMode.class) : PipelineMode.CASCADE;
    }
}
