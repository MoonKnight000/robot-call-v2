package uz.murodjon.robotcallv2.mcp.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDraftDiffTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void onlyChangedFieldsAreListedOneLevelIntoObjects() throws Exception {
        ObjectNode changes = AgentDraftDiff.diff(
                json.readTree("""
                        {"id": 1, "name": "A", "language": "uz-UZ", "script": {"firstMessage": "Salom", "systemPrompt": "eski"}, "updatedAt": "x"}"""),
                json.readTree("""
                        {"id": 1, "name": "A", "language": "ru-RU", "script": {"firstMessage": "Salom", "systemPrompt": "yangi"}, "updatedAt": "y"}"""));

        assertThat(changes.fieldNames()).toIterable().containsExactlyInAnyOrder("language", "script");
        assertThat(changes.at("/language/published").asText()).isEqualTo("uz-UZ");
        assertThat(changes.at("/language/draft").asText()).isEqualTo("ru-RU");
        assertThat(changes.at("/script/systemPrompt/draft").asText()).isEqualTo("yangi");
        assertThat(changes.at("/script/firstMessage").isMissingNode()).isTrue();
    }

    @Test
    void anAgentThatTakesNoCallsIsOffline() {
        assertThat(AgentDraftPolicy.isOffline(false, null)).isTrue();
        assertThat(AgentDraftPolicy.isOffline(true, null)).isFalse();
        assertThat(AgentDraftPolicy.isOffline(false, 2)).isFalse();
    }
}
