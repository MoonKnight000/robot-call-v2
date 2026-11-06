package uz.murodjon.robotcallv2.agent.turn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmTurnDetectorUsageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reportsTheAnswersTokensUnderTheAskedModel() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {"model":"models/gemini-3.5-flash-lite","choices":[{"message":{"content":"finished"}}],
                 "usage":{"prompt_tokens":640,"completion_tokens":2,
                          "prompt_tokens_details":{"cached_tokens":512}}}""");
        List<String> recorded = new ArrayList<>();

        LlmTurnDetector.reportUsage(body, "gemini-3.5-flash-lite",
                (model, prompt, completion, cached) -> recorded.add(model + " " + prompt + " " + completion + " " + cached));

        assertThat(recorded).containsExactly("gemini-3.5-flash-lite 640 2 512");
    }

    @Test
    void anAnswerWithoutUsageIsNotCounted() throws Exception {
        JsonNode body = objectMapper.readTree("{\"choices\":[{\"message\":{\"content\":\"finished\"}}]}");
        List<String> recorded = new ArrayList<>();

        LlmTurnDetector.reportUsage(body, "gpt-5.6-luna",
                (model, prompt, completion, cached) -> recorded.add(model));

        assertThat(recorded).isEmpty();
    }
}
