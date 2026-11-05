package uz.murodjon.robotcallv2.copilot.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotPageContext;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotToolSelectorTest {

    private final List<McpTool> sampleTools = List.of(
            new McpTool("list_agents", "desc", "{}", true, false),
            new McpTool("get_agent", "desc", "{}", true, false),
            new McpTool("create_agent", "desc", "{}", false, false),
            new McpTool("update_agent_draft", "desc", "{}", false, false),
            new McpTool("list_scenarios", "desc", "{}", true, false),
            new McpTool("list_campaigns", "desc", "{}", true, false),
            new McpTool("get_campaign", "desc", "{}", true, false),
            new McpTool("create_campaign_draft", "desc", "{}", false, false),
            new McpTool("get_call_stats", "desc", "{}", true, false),
            new McpTool("list_calls", "desc", "{}", true, false),
            new McpTool("get_call", "desc", "{}", true, false),
            new McpTool("list_knowledge", "desc", "{}", true, false)
    );

    @Test
    void selectsAgentToolsWhenOnAgentEditorPage() {
        CopilotPageContext context = new CopilotPageContext("agent-editor", 4L, null, null, null);
        List<McpTool> selected = CopilotToolSelector.selectTools(context, "Salom, agentga yordam ber", sampleTools);

        List<String> names = selected.stream().map(McpTool::name).toList();
        assertThat(names).contains("get_agent", "create_agent", "update_agent_draft", "list_scenarios");
        assertThat(names).doesNotContain("create_campaign_draft", "list_calls");
    }

    @Test
    void selectsAnalyticsToolsWhenOnReportsPage() {
        CopilotPageContext context = new CopilotPageContext("reports", null, null, null, null);
        List<McpTool> selected = CopilotToolSelector.selectTools(context, "Kechagi statistikani ko'rsat", sampleTools);

        List<String> names = selected.stream().map(McpTool::name).toList();
        assertThat(names).contains("get_call_stats", "list_calls", "get_call");
        assertThat(names).doesNotContain("create_campaign_draft", "create_agent");
    }

    @Test
    void selectsToolsByKeywordsWhenPageIsGeneral() {
        CopilotPageContext context = new CopilotPageContext("dashboard", null, null, null, null);
        List<McpTool> selected = CopilotToolSelector.selectTools(context, "Nega qo'ng'iroqlarda transfer ko'paydi?", sampleTools);

        List<String> names = selected.stream().map(McpTool::name).toList();
        assertThat(names).contains("get_call_stats", "list_calls", "get_call");
    }

    @Test
    void minifiesJsonOutputAndTruncatesSafely() {
        String input = """
                {
                  "name": "Test",
                  "emptyVal": null,
                  "count": 10,
                  "list": [1, 2, 3]
                }
                """;
        String minified = CopilotToolResultMinifier.minify("test_tool", input);
        assertThat(minified).doesNotContain("emptyVal");
        assertThat(minified).contains("\"name\":\"Test\"");
    }
}
