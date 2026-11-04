package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnToolsRunTimeTest {

    @Test
    void aToolThatHasNotRunHasNoExpectedTime() {
        TurnTools turnTools = new TurnTools(mock(DialogProperties.class), null);

        assertThat(turnTools.findExpectedRunMs(1L, "searchApartments")).isEqualTo(-1);
    }

    @Test
    void aToolsRunIsRememberedPerCompany() {
        TurnTools turnTools = new TurnTools(mock(DialogProperties.class), null);
        DialogSession s = mock(DialogSession.class);
        when(s.companyId()).thenReturn(1L);
        ToolCallback tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("searchApartments").description("search").inputSchema("{}").build());
        when(tool.call("{}")).thenReturn("3 complexes");

        turnTools.run(s, List.of(tool),
                List.of(new AssistantMessage.ToolCall("1", "function", "searchApartments", "{}")));

        assertThat(turnTools.findExpectedRunMs(1L, "searchApartments")).isBetween(0L, 500L);
        assertThat(turnTools.findExpectedRunMs(2L, "searchApartments")).isEqualTo(-1);
    }
}
