package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** How a tool's {@code result} settings shape what the model is told: list limit, empty answer, failure. */
class ToolResponseFormatterResultTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ToolResponseFormatter formatter = new ToolResponseFormatter(JSON);
    private final DialogOutcomeSink session = mock(DialogOutcomeSink.class);

    private static final String TEN_COMPLEXES = """
            {"data": [{"name": "A", "blocks": [1, 2, 3, 4]}, {"name": "B"}, {"name": "C"}, {"name": "D"},
              {"name": "E"}, {"name": "F"}, {"name": "G"}, {"name": "H"}, {"name": "I"}, {"name": "J"}]}""";

    private static Tool tool(ToolResult result, Map<String, String> mapping) {
        return new Tool(null, null, "listComplexes", "Lists complexes.",
                new ToolHttpConfig("https://crm.example/houses", "GET", null, null, null, null, null, null, 0,
                        null, null, null),
                null, null, mapping, result, 3, null, null, null, null, null);
    }

    private JsonNode format(Tool tool, int status, String body) throws Exception {
        return JSON.readTree(formatter.formatForModel(tool, session, status, body));
    }

    @Test
    void everyListIsCutToMaxItemsNestedOnesToo() throws Exception {
        JsonNode answer = format(tool(new ToolResult(3, null, null, null, null), null), 200, TEN_COMPLEXES);

        assertThat(answer.at("/data/data")).hasSize(3);
        assertThat(answer.at("/data/data/2/name").asText()).isEqualTo("C");
        assertThat(answer.at("/data/data/0/blocks")).hasSize(3);
    }

    @Test
    void maxItemsZeroShowsTheWholeList() throws Exception {
        JsonNode answer = format(tool(ToolResult.NONE, null), 200, TEN_COMPLEXES);

        assertThat(answer.at("/data/data")).hasSize(10);
    }

    @Test
    void theLimitAppliesAfterTheMapping() throws Exception {
        JsonNode answer = format(tool(new ToolResult(2, null, null, null, null), Map.of("names", "$.data[*].name")), 200,
                TEN_COMPLEXES);

        assertThat(answer.at("/data/names")).hasSize(2);
    }

    @Test
    void anEmptyAnswerIsReplacedByEmptyMessage() throws Exception {
        JsonNode answer = format(tool(new ToolResult(0, "Nothing on sale — offer a callback.", null, null, null), null), 200,
                "{\"data\": []}");

        assertThat(answer.path("message").asText()).isEqualTo("Nothing on sale — offer a callback.");
        assertThat(answer.has("data")).isFalse();
        verify(session, never()).recordToolFigures(anyCollection());
    }

    @Test
    void anAnswerWithSomethingInItIsNotEmpty() throws Exception {
        JsonNode answer = format(tool(new ToolResult(0, "empty", null, null, null), null), 200, "{\"total\": 0, \"data\": []}");

        assertThat(answer.has("message")).isFalse();
        assertThat(answer.at("/data/total").asInt()).isZero();
    }

    @Test
    void withoutEmptyMessageAnEmptyAnswerPassesThrough() throws Exception {
        JsonNode answer = format(tool(ToolResult.NONE, null), 200, "{\"data\": []}");

        assertThat(answer.at("/data/data").isArray()).isTrue();
    }

    @Test
    void aFailedAnswerIsReplacedByErrorMessage() throws Exception {
        JsonNode answer = format(tool(new ToolResult(0, null, "The CRM is down — carry on without it.", null, null), null), 403,
                "{\"message\": \"Forbidden\"}");

        assertThat(answer.path("status").asInt()).isEqualTo(403);
        assertThat(answer.path("error").asText()).isEqualTo("The CRM is down — carry on without it.");
        assertThat(answer.has("data")).isFalse();
    }

    @Test
    void withoutErrorMessageAFailedAnswerKeepsItsBody() throws Exception {
        JsonNode answer = format(tool(ToolResult.NONE, null), 403, "{\"message\": \"Forbidden\"}");

        assertThat(answer.at("/data/message").asText()).isEqualTo("Forbidden");
        verify(session, never()).recordOutcome(any(), any());
    }

    @Test
    void aRequestThatNeverWentThroughSaysErrorMessage() throws Exception {
        Tool withMessage = tool(new ToolResult(0, null, "Say a specialist will call back.", null, null), null);

        assertThat(JSON.readTree(formatter.formatFailed(withMessage)).path("error").asText())
                .isEqualTo("Say a specialist will call back.");
        assertThat(JSON.readTree(formatter.formatBlocked(withMessage)).path("error").asText())
                .isEqualTo("Say a specialist will call back.");
        assertThat(JSON.readTree(formatter.formatFailed(tool(ToolResult.NONE, null))).path("error").asText())
                .isEqualTo("Tool execution failed");
    }

    @Test
    void blankMessagesAreUnset() {
        ToolResult result = new ToolResult(-4, "  ", "", null, null);

        assertThat(result.maxItems()).isZero();
        assertThat(result.emptyMessage()).isNull();
        assertThat(result.errorMessage()).isNull();
    }
}
