package uz.murodjon.robotcallv2.copilot.domain.service;

import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotPageContext;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;

import java.util.*;

/**
 * Selects the optimal subset of available MCP tools for a Copilot request based on
 * the active page context, focused entity IDs, and user intent keywords.
 *
 * <p>Deliberately prunes unneeded tool schemas from the model's prompt. Passing all 35+
 * tool definitions consumes 15,000+ tokens on every turn. Dynamically selecting only
 * the 6-10 tools genuinely relevant to the current page and question drops input prompt
 * tokens by ~80%, lowers TTFT latency, and prevents tool hallucination.
 */
public final class CopilotToolSelector {

    private static final Set<String> CORE_DISCOVERY_TOOLS = Set.of(
            "list_agents", "get_agent", "list_campaigns", "get_call_stats"
    );

    private static final Set<String> AGENT_TOOLS = Set.of(
            "list_agents", "get_agent", "create_agent", "update_agent_draft", "diff_agent_draft",
            "list_voices", "list_llm_models", "list_knowledge", "add_knowledge_item",
            "list_tools", "create_rest_tool", "attach_tool_to_agent", "list_inbound_routes",
            "list_scenarios", "get_scenario", "create_scenario", "clone_scenario", "update_scenario",
            "upsert_scenario_stage", "upsert_global_node", "validate_scenario",
            "list_test_cases", "create_test_case", "run_evaluation", "get_evaluation_run",
            "export_agent", "import_agent_pack"
    );

    private static final Set<String> SCENARIO_TOOLS = Set.of(
            "list_scenarios", "get_scenario", "validate_scenario", "create_scenario",
            "clone_scenario", "update_scenario", "upsert_scenario_stage", "upsert_global_node",
            "get_agent", "list_agents"
    );

    private static final Set<String> CAMPAIGN_TOOLS = Set.of(
            "list_campaigns", "get_campaign", "create_campaign_draft", "update_campaign",
            "import_contacts", "compare_campaigns", "list_agents", "get_agent", "get_call_stats"
    );

    private static final Set<String> ANALYTICS_CALL_TOOLS = Set.of(
            "get_call_stats", "compare_periods", "get_call_timeseries", "get_stage_funnel",
            "get_call_topics", "get_hourly_heatmap", "get_call_trace", "list_agent_insights",
            "ask_calls", "list_calls", "get_call", "list_agents", "get_agent", "list_campaigns"
    );

    private static final Set<String> EVALUATION_TOOLS = Set.of(
            "run_evaluation", "get_evaluation_run", "list_test_cases", "create_test_case",
            "get_agent", "list_agents", "update_agent_draft"
    );

    private static final Set<String> KNOWLEDGE_TOOLS = Set.of(
            "list_knowledge", "add_knowledge_item", "get_agent", "list_agents", "update_agent_draft"
    );

    private static final Set<String> CHAT_TOOLS = Set.of(
            "list_chats", "get_chat"
    );

    private CopilotToolSelector() {
    }

    /**
     * Filters available tools down to the relevant set for this specific turn.
     *
     * @param context        page and focused entity IDs (agentId, campaignId, callId)
     * @param userMessage    the question text (analyzed for intent keywords)
     * @param availableTools tools the user has permission to see
     * @return pruned list of tools preserving the original catalog ordering
     */
    public static List<McpTool> selectTools(CopilotPageContext context, String userMessage, List<McpTool> availableTools) {
        if (availableTools == null || availableTools.isEmpty()) {
            return List.of();
        }
        if (availableTools.size() <= 8) {
            return availableTools;
        }

        Set<String> matchedToolNames = new HashSet<>();
        boolean pageMatched = false;

        if (context != null && context.page() != null && !context.page().isBlank()) {
            String page = context.page().toLowerCase(Locale.ROOT).trim();
            if (page.contains("agent") || page.contains("bot")) {
                matchedToolNames.addAll(AGENT_TOOLS);
                pageMatched = true;
            } else if (page.contains("scenario")) {
                matchedToolNames.addAll(SCENARIO_TOOLS);
                pageMatched = true;
            } else if (page.contains("campaign")) {
                matchedToolNames.addAll(CAMPAIGN_TOOLS);
                pageMatched = true;
            } else if (page.contains("report") || page.contains("call") || page.contains("analytic") || page.contains("stat")) {
                matchedToolNames.addAll(ANALYTICS_CALL_TOOLS);
                pageMatched = true;
            } else if (page.contains("eval")) {
                matchedToolNames.addAll(EVALUATION_TOOLS);
                pageMatched = true;
            } else if (page.contains("knowledge")) {
                matchedToolNames.addAll(KNOWLEDGE_TOOLS);
                pageMatched = true;
            } else if (page.contains("chat")) {
                matchedToolNames.addAll(CHAT_TOOLS);
                pageMatched = true;
            }
        }

        // Entity IDs focused in context
        if (context != null) {
            if (context.agentId() != null) {
                matchedToolNames.add("get_agent");
                matchedToolNames.add("update_agent_draft");
                matchedToolNames.add("diff_agent_draft");
                matchedToolNames.add("run_evaluation");
            }
            if (context.campaignId() != null) {
                matchedToolNames.add("get_campaign");
                matchedToolNames.add("update_campaign");
                matchedToolNames.add("get_call_stats");
            }
            if (context.callId() != null) {
                matchedToolNames.add("get_call");
                matchedToolNames.add("get_call_trace");
                matchedToolNames.add("list_calls");
            }
            if (context.scenarioId() != null) {
                matchedToolNames.add("get_scenario");
                matchedToolNames.add("update_scenario");
                matchedToolNames.add("upsert_scenario_stage");
            }
        }

        // Keyword Intent Matching
        if (userMessage != null && !userMessage.isBlank()) {
            String text = userMessage.toLowerCase(Locale.ROOT);

            if (containsAny(text, "qo'ng'iroq", "qo'ngiroq", "qongiroq", "call", "звонок", "tahlil", "analiz",
                    "statistika", "transfer", "funnel", "topic", "shikoyat", "heatmap", "vaqt", "davomiylik",
                    "trend", "konversiya", "natija", "o'sdi", "tushdi", "kamaydi", "nega", "pochemu", "why")) {
                matchedToolNames.addAll(ANALYTICS_CALL_TOOLS);
            }
            if (containsAny(text, "kampaniya", "campaign", "kontakt", "contact", "dial", "target", "baza",
                    "ro'yxat", "auditoriya", "qayta")) {
                matchedToolNames.addAll(CAMPAIGN_TOOLS);
            }
            if (containsAny(text, "agent", "prompt", "ovoz", "voice", "ssenariy", "scenario", "bosqich", "stage",
                    "bilim", "knowledge", "sozla", "yarat", "tahrir", "qoralama", "draft", "test", "eval",
                    "yoz", "gapir", "so'z", "salom")) {
                matchedToolNames.addAll(AGENT_TOOLS);
            }
            if (containsAny(text, "chat", "yozishma", "xabar", "sms")) {
                matchedToolNames.addAll(CHAT_TOOLS);
            }
        }

        // Fallback for general queries without clear page or keyword matches
        if (!pageMatched && matchedToolNames.isEmpty()) {
            matchedToolNames.addAll(CORE_DISCOVERY_TOOLS);
            matchedToolNames.add("create_agent");
            matchedToolNames.add("update_agent_draft");
            matchedToolNames.add("list_calls");
            matchedToolNames.add("get_call");
            matchedToolNames.add("create_campaign_draft");
            matchedToolNames.add("list_knowledge");
        } else {
            // Always keep discovery tools so the model can navigate between resources
            matchedToolNames.add("get_agent");
            matchedToolNames.add("get_campaign");
            matchedToolNames.add("list_agents");
        }

        List<McpTool> filtered = new ArrayList<>();
        for (McpTool tool : availableTools) {
            if (matchedToolNames.contains(tool.name())) {
                filtered.add(tool);
            }
        }

        return filtered.isEmpty() ? availableTools : filtered;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
