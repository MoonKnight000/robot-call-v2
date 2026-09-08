package uz.murodjon.robotcallv2.mcp.domain.service;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The model is on a live call with a stranger who can say anything, so what is pinned down
 * here is what a talked-into model must never be able to reach.
 */
class McpToolGuardTest {

    private static McpTool tool(String name, boolean readOnly, boolean destructive) {
        return new McpTool(name, "", null, readOnly, destructive);
    }

    /** No wording of "are you sure" survives a determined caller. */
    @Test
    void neverOffersADestructiveTool() {
        List<McpTool> usable = McpToolGuard.filterUsable(
                List.of(tool("deleteOrder", false, true), tool("refund", true, true)), true);

        assertThat(usable).isEmpty();
    }

    @Test
    void alwaysOffersAReadOnlyTool() {
        assertThat(McpToolGuard.filterUsable(List.of(tool("findOrder", true, false)), false))
                .extracting(McpTool::name)
                .containsExactly("findOrder");
    }

    /** A tool that writes but does not destroy waits for the company to say so. */
    @Test
    void offersAWriteToolOnlyWhenTheCompanyAllowedIt() {
        List<McpTool> writes = List.of(tool("bookSlot", false, false));

        assertThat(McpToolGuard.filterUsable(writes, false)).isEmpty();
        assertThat(McpToolGuard.filterUsable(writes, true)).extracting(McpTool::name)
                .containsExactly("bookSlot");
    }

    @Test
    void dropsToolsWithNoUsableName() {
        assertThat(McpToolGuard.filterUsable(
                List.of(tool(null, true, false), tool("  ", true, false)), true)).isEmpty();
        assertThat(McpToolGuard.filterUsable(null, true)).isEmpty();
        assertThat(McpToolGuard.filterUsable(List.of(), true)).isEmpty();
    }
}
