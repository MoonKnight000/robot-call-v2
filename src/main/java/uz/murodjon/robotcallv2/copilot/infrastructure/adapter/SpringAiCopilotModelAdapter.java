package uz.murodjon.robotcallv2.copilot.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotModelPort;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotToolInvoker;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotModelReply;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotMessageRole;
import uz.murodjon.robotcallv2.copilot.infrastructure.config.CopilotProperties;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.util.List;
import java.util.Map;

/**
 * The copilot on a Spring AI chat model. Spring AI runs the tool rounds itself: every tool the
 * model asks for goes to the {@link CopilotToolInvoker} on this thread — the request's, so the
 * caller's permissions apply — and the final answer carries the usage of all the rounds.
 */
@Component
public class SpringAiCopilotModelAdapter implements CopilotModelPort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final CopilotProperties copilotProperties;

    public SpringAiCopilotModelAdapter(ObjectProvider<ChatModel> chatModelProvider,
                                       CopilotProperties copilotProperties) {
        this.chatModelProvider = chatModelProvider;
        this.copilotProperties = copilotProperties;
    }

    @Override
    public CopilotModelReply generateReply(String instructions, List<CopilotMessage> history, List<McpTool> tools,
                                           CopilotToolInvoker invoker) {
        List<ToolCallback> callbacks = tools.stream().map(tool -> toCallback(tool, invoker)).toList();
        return call(instructions, toMessages(history), callbacks);
    }

    @Override
    public CopilotModelReply generateText(String instructions, String message) {
        return call(instructions, List.of(new UserMessage(message)), List.of());
    }

    private CopilotModelReply call(String instructions, List<Message> messages, List<ToolCallback> callbacks) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new ConflictException(ErrorCode.COPILOT_NOT_CONFIGURED);
        }
        ChatResponse response;
        try {
            response = ChatClient.create(chatModel).prompt()
                    .options(options(chatModel, callbacks).mutate())
                    .system(instructions)
                    .messages(messages)
                    .call()
                    .chatResponse();
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ExternalServiceException(ErrorCode.COPILOT_REPLY_FAILED, "llm", e, e.getMessage());
        }
        String text = response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new ExternalServiceException(ErrorCode.COPILOT_REPLY_FAILED, "llm", "empty answer");
        }
        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        return new CopilotModelReply(text.strip(), tokens(usage == null ? null : usage.getPromptTokens()),
                tokens(usage == null ? null : usage.getCompletionTokens()));
    }

    private ChatOptions options(ChatModel chatModel, List<ToolCallback> callbacks) {
        String model = copilotProperties.model();
        boolean openAi = chatModel instanceof OpenAiChatModel
                || model.startsWith("gpt-") || model.startsWith("llama") || model.startsWith("qwen");
        return openAi
                ? OpenAiChatOptions.builder().model(model).maxTokens(copilotProperties.maxTokens())
                        .toolCallbacks(callbacks).build()
                : GoogleGenAiChatOptions.builder().model(model).maxOutputTokens(copilotProperties.maxTokens())
                        .toolCallbacks(callbacks).build();
    }

    private static ToolCallback toCallback(McpTool tool, CopilotToolInvoker invoker) {
        return FunctionToolCallback
                .<Map<String, Object>, String>builder(tool.name(),
                        arguments -> invoker.callTool(tool.name(), toJson(arguments)))
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .inputType(Map.class)
                .build();
    }

    private static List<Message> toMessages(List<CopilotMessage> history) {
        return history.stream()
                .map(message -> message.role() == CopilotMessageRole.ASSISTANT
                        ? (Message) new AssistantMessage(message.content())
                        : new UserMessage(message.content()))
                .toList();
    }

    private static String toJson(Map<String, Object> arguments) {
        try {
            return JSON.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("tool arguments the model sent as JSON cannot be written back as JSON", e);
        }
    }

    private static long tokens(Integer value) {
        return value == null ? 0 : value;
    }
}
