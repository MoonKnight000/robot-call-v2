package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageTest {

    @Test
    void streamedCallWithCumulativeUsageCountsOnce() {
        // Gemini: the same prompt on every chunk, the completion growing to its final figure.
        TokenUsage usage = new TokenUsage();
        usage.add(chunk(7293, 16));
        usage.add(chunk(7293, 44));
        usage.add(chunk(7293, 68));
        usage.add(chunk(7293, 68));

        assertThat(usage.promptTokens()).isEqualTo(7293);
        assertThat(usage.completionTokens()).isEqualTo(68);
    }

    @Test
    void secondCallOfATurnIsAdded() {
        TokenUsage usage = new TokenUsage();
        usage.add(chunk(7293, 20));
        usage.add(chunk(7293, 30));
        // The tool round: a longer prompt, its own completion from zero.
        usage.add(chunk(7400, 10));
        usage.add(chunk(7400, 25));

        assertThat(usage.promptTokens()).isEqualTo(7293 + 7400);
        assertThat(usage.completionTokens()).isEqualTo(30 + 25);
    }

    @Test
    void chunksWithoutUsageAreIgnored() {
        TokenUsage usage = new TokenUsage();
        usage.add(chunk(0, 0));
        usage.add(chunk(100, 5));

        assertThat(usage.promptTokens()).isEqualTo(100);
        assertThat(usage.completionTokens()).isEqualTo(5);
    }

    @Test
    void openAiCachedPromptIsCounted() {
        // OpenAI's prompt_tokens_details.cached_tokens arrives as the generic cache-read count.
        TokenUsage usage = new TokenUsage();
        usage.add(new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(new DefaultUsage(7214, 20, 7234, null, 7211L, null))
                .build()));

        assertThat(usage.cachedTokens()).isEqualTo(7211);
    }

    @Test
    void cachedCountNeverExceedsThePrompt() {
        assertThat(TokenUsage.cachedTokens(new DefaultUsage(100, 5, 105, null, 900L, null))).isEqualTo(100);
    }

    private static ChatResponse chunk(int prompt, int completion) {
        return new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(new DefaultUsage(prompt, completion, prompt + completion))
                .build());
    }
}
