package uz.murodjon.robotcallv2.pack.infrastructure.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.pack.application.port.output.PackGeneratorPort;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.pack.infrastructure.config.PackGeneratorProperties;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.util.PromptText;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.List;

/**
 * Writes a pack draft with a chat model (UNIVERSAL-OPERATOR-PLAN E.1).
 *
 * <p>The model is handed the shape of a pack and the tools that already exist for this API, and
 * writes the conversation around them. It is told twice, in the instructions and by the shape
 * itself, that the {@code tools} array is not its to fill: an endpoint invented here would be a
 * call placed to an address nobody checked.
 *
 * <p>Read leniently on purpose, unlike the packs that ship with the build: those are ours and a
 * misspelt key in one is a defect to stop the application for, while a model adding a field it
 * was not asked for should cost a shrug and not a draft.
 */
@Component
public class SpringAiPackGeneratorAdapter implements PackGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiPackGeneratorAdapter.class);

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String INSTRUCTIONS = PromptText.text("pack-generator.txt");

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final PackGeneratorProperties packGeneratorProperties;

    public SpringAiPackGeneratorAdapter(ObjectProvider<ChatModel> chatModelProvider,
                                        PackGeneratorProperties packGeneratorProperties) {
        this.chatModelProvider = chatModelProvider;
        this.packGeneratorProperties = packGeneratorProperties;
    }

    @Override
    public VerticalPack generate(String business, String language, List<Tool> tools) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new ConflictException(ErrorCode.PACK_GENERATOR_NOT_CONFIGURED);
        }
        String answer;
        try {
            answer = ChatClient.create(chatModel).prompt()
                    .options(options(chatModel).mutate())
                    .system(INSTRUCTIONS)
                    .user(describe(business, language, tools))
                    .call()
                    .content();
        } catch (RuntimeException e) {
            throw new ExternalServiceException(ErrorCode.PACK_GENERATION_FAILED, "llm", e);
        }
        return read(answer);
    }

    private ChatOptions options(ChatModel chatModel) {
        String model = packGeneratorProperties.model();
        boolean openAi = chatModel instanceof OpenAiChatModel
                || model.startsWith("gpt-") || model.startsWith("llama") || model.startsWith("qwen");
        return openAi
                ? OpenAiChatOptions.builder().model(model).maxTokens(packGeneratorProperties.maxTokens()).build()
                : GoogleGenAiChatOptions.builder().model(model)
                        .maxOutputTokens(packGeneratorProperties.maxTokens()).build();
    }

    private static String describe(String business, String language, List<Tool> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("BUSINESS:\n").append(business).append("\n\n");
        sb.append("AGENT LANGUAGE: ").append(language != null && !language.isBlank() ? language : "uz-UZ").append('\n');
        if (tools.isEmpty()) {
            sb.append("\nTOOLS: none. The agent writes to no external system — the outcome is gathered from the "
                    + "conversation alone, so the goal field must be a value filled in by the scenario itself.\n");
            return sb.toString();
        }
        sb.append("\nTOOLS THE AGENT WILL HAVE (taken from the API document, do not change them):\n");
        for (Tool tool : tools) {
            sb.append("- ").append(tool.name()).append(" (").append(tool.apiMethod()).append("): ")
                    .append(tool.description() == null ? "" : tool.description()).append('\n');
        }
        sb.append("Use these names in the stages, and have the agent say \"qabul qilindi\" only after "
                + "the writing tool has been called.\n");
        return sb.toString();
    }

    /** The model's answer as a pack; a fenced block is unwrapped, anything unreadable is a 502. */
    private static VerticalPack read(String answer) {
        String json = answer == null ? "" : answer.trim();
        if (json.startsWith("```")) {
            int firstBreak = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            json = firstBreak > 0 && lastFence > firstBreak ? json.substring(firstBreak + 1, lastFence).trim() : json;
        }
        try {
            VerticalPack pack = JSON.readValue(json, VerticalPack.class);
            if (pack == null || pack.agent() == null || pack.agent().scenario() == null) {
                throw new ExternalServiceException(ErrorCode.PACK_GENERATION_FAILED, "llm");
            }
            return pack;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Pack generation produced unreadable JSON: {}", e.getMessage());
            throw new ExternalServiceException(ErrorCode.PACK_GENERATION_FAILED, "llm", e);
        }
    }
}
