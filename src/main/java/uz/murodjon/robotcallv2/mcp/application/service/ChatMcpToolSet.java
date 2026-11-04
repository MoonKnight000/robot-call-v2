package uz.murodjon.robotcallv2.mcp.application.service;

import jakarta.validation.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatHistoryUseCase;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatFilter;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Chat conversations (widget, Telegram, API) for the copilot's analysis: read only. */
@Component
@Order(50)
public class ChatMcpToolSet implements McpToolSet {

    private static final Set<Permission> CHAT_READ = EnumSet.of(Permission.CHAT_AGENT_READ, Permission.CHAT_AGENT_EDIT);

    private final ChatHistoryUseCase chatHistoryUseCase;
    private final McpToolArguments arguments;

    public ChatMcpToolSet(ChatHistoryUseCase chatHistoryUseCase, Validator validator) {
        this.chatHistoryUseCase = chatHistoryUseCase;
        this.arguments = new McpToolArguments(validator);
    }

    @Override
    public List<McpToolDefinition> findTools() {
        return List.of(
                new McpToolDefinition("list_chats", """
                        The company's chat conversations, newest first; totalElements counts every match, so \
                        a filter with size 1 is a count (e.g. how many NEGATIVE chats this week).""", CHAT_READ, """
                        {"type": "object", "properties": {
                          "aiAgentId": {"type": "integer"},
                          "channel": {"type": "string", "enum": ["WIDGET", "TELEGRAM", "API"]},
                          "ended": {"type": "boolean"},
                          "from": {"type": "string", "format": "date-time"},
                          "to": {"type": "string", "format": "date-time"},
                          "sentiment": {"type": "string", "enum": ["POSITIVE", "NEUTRAL", "NEGATIVE", "HOSTILE"]},
                          "needsFollowUp": {"type": "boolean"},
                          "page": {"type": "integer", "minimum": 0},
                          "size": {"type": "integer", "minimum": 1, "maximum": 100}}}""",
                        (companyId, tool, args, draftOnly) -> chatHistoryUseCase.findChats(companyId,
                                arguments.convert(tool, args, ChatFilter.class))),

                new McpToolDefinition("get_chat", """
                        One chat in full: transcript, what the scenario recorded, and the post-chat analysis \
                        (summary, sentiment, needsFollowUp, extracted fields).""", CHAT_READ, """
                        {"type": "object", "required": ["chatId"], "properties": {"chatId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> chatHistoryUseCase.requireChat(companyId,
                                arguments.requireId(tool, args, "chatId"))));
    }
}
