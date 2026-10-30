package uz.murodjon.robotcallv2.agent.provider;

import com.google.genai.Client;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The platform's text-LLM chat models, one per vendor, for whichever vendors have a key.
 *
 * <p>Spring AI auto-configures only the vendor {@code spring.ai.model.chat} names (the
 * switch is a single value, and Gemini's auto-configuration refuses to start without its
 * key), so that one is taken as the bean it made — with its YAML tuning intact — and the
 * others are built here, once, the first time a request needs them.
 */
@Component
public class ChatModelRegistry {

    /** A turn's own deadline ({@code LlmTimeoutProperties}) is far shorter; this is only a cap. */
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final ChatProviderProperties chatProviderProperties;
    private final ObjectProvider<GoogleGenAiChatModel> autoConfiguredGoogle;
    private final ObjectProvider<OpenAiChatModel> autoConfiguredOpenAi;
    private final ObjectProvider<AnthropicChatModel> autoConfiguredAnthropic;
    private final Map<ChatVendor, ChatModel> built = new ConcurrentHashMap<>();

    public ChatModelRegistry(ChatProviderProperties chatProviderProperties,
                             ObjectProvider<GoogleGenAiChatModel> autoConfiguredGoogle,
                             ObjectProvider<OpenAiChatModel> autoConfiguredOpenAi,
                             ObjectProvider<AnthropicChatModel> autoConfiguredAnthropic) {
        this.chatProviderProperties = chatProviderProperties;
        this.autoConfiguredGoogle = autoConfiguredGoogle;
        this.autoConfiguredOpenAi = autoConfiguredOpenAi;
        this.autoConfiguredAnthropic = autoConfiguredAnthropic;
    }

    /** Whether this deployment holds a key for the vendor, which is what makes it selectable. */
    public boolean isAvailable(ChatVendor vendor) {
        return vendor != null && !apiKey(vendor).isBlank();
    }

    /** Whether {@link #findByVendor} would return a model for this {@code ai_model.provider} value. */
    public boolean isAvailable(String provider) {
        return isAvailable(ChatVendor.ofProvider(provider));
    }

    /** @return the vendor requests with no (or an unrecognised) model id run on */
    public ChatVendor findDefaultVendor() {
        ChatVendor configured = ChatVendor.ofProvider(chatProviderProperties.defaultProvider());
        if (isAvailable(configured)) {
            return configured;
        }
        // The configured default has no key: run on whichever vendor does have one.
        for (ChatVendor vendor : ChatVendor.values()) {
            if (isAvailable(vendor)) {
                return vendor;
            }
        }
        return null;
    }

    /** @return the vendor's chat model, or {@code null} when the deployment has no key for it */
    public ChatModel findByVendor(ChatVendor vendor) {
        if (!isAvailable(vendor)) {
            return null;
        }
        ChatModel autoConfigured = switch (vendor) {
            case GOOGLE_GENAI -> autoConfiguredGoogle.getIfAvailable();
            case OPENAI -> autoConfiguredOpenAi.getIfAvailable();
            case ANTHROPIC -> autoConfiguredAnthropic.getIfAvailable();
        };
        return autoConfigured != null ? autoConfigured : built.computeIfAbsent(vendor, this::build);
    }

    private ChatModel build(ChatVendor vendor) {
        return switch (vendor) {
            case GOOGLE_GENAI -> GoogleGenAiChatModel.builder()
                    .genAiClient(Client.builder().apiKey(chatProviderProperties.geminiApiKey()).build())
                    .options(GoogleGenAiChatOptions.builder()
                            .model("gemini-3.8-flash")
                            .maxOutputTokens(MAX_OUTPUT_TOKENS)
                            .thinkingLevel(GoogleGenAiThinkingLevel.LOW)
                            .build())
                    .build();
            case OPENAI -> OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .apiKey(chatProviderProperties.openAiApiKey())
                            .baseUrl(chatProviderProperties.openAiBaseUrl())
                            .model("gpt-5.6-luna")
                            .maxTokens(MAX_OUTPUT_TOKENS)
                            .build())
                    .build();
            case ANTHROPIC -> AnthropicChatModel.builder()
                    .options(AnthropicChatOptions.builder()
                            .apiKey(chatProviderProperties.anthropicApiKey())
                            .model("claude-haiku-5-5")
                            .maxTokens(MAX_OUTPUT_TOKENS)
                            .build())
                    .build();
        };
    }

    private String apiKey(ChatVendor vendor) {
        String key = switch (vendor) {
            case GOOGLE_GENAI -> chatProviderProperties.geminiApiKey();
            case OPENAI -> chatProviderProperties.openAiApiKey();
            case ANTHROPIC -> chatProviderProperties.anthropicApiKey();
        };
        return key == null ? "" : key;
    }
}
