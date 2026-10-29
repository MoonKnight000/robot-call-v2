package uz.murodjon.robotcallv2.chat.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.ChatRow;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatHistoryUseCase;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatFilter;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatChannel;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.List;
import java.util.Map;

/**
 * What a supervisor reads of the widget and Telegram conversations: the rows the channels
 * write, never written to from here.
 */
@Service
public class ChatHistoryService implements ChatHistoryUseCase {

    private final ChatSessionRepository repository;
    private final AiAgentUseCase aiAgentUseCase;

    public ChatHistoryService(ChatSessionRepository repository, AiAgentUseCase aiAgentUseCase) {
        this.repository = repository;
        this.aiAgentUseCase = aiAgentUseCase;
    }

    @Override
    public PageableData<ChatRow> findChats(long companyId, ChatFilter filter) {
        List<ChatSession> sessions = repository.findAll(companyId, filter);
        Map<Long, String> agentNames = aiAgentUseCase.findNamesByIds(
                sessions.stream().map(ChatSession::aiAgentId).distinct().toList());
        List<ChatRow> rows = sessions.stream()
                .map(session -> toRow(session, agentNames.get(session.aiAgentId())))
                .toList();
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(),
                repository.count(companyId, filter));
    }

    @Override
    public ChatDetail requireChat(long companyId, long id) {
        ChatSession session = repository.findByCompanyIdAndId(companyId, id);
        if (session == null) {
            throw new NotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND, id);
        }
        String agentName = aiAgentUseCase.findNamesByIds(List.of(session.aiAgentId())).get(session.aiAgentId());
        return new ChatDetail(toRow(session, agentName), session.outcome(), repository.findMessages(session.id()));
    }

    private static ChatRow toRow(ChatSession session, String agentName) {
        ChatChannel channel = session.telegramBotId() != null ? ChatChannel.TELEGRAM : ChatChannel.WIDGET;
        return new ChatRow(session.id(), session.aiAgentId(), agentName, channel, session.widgetId(),
                session.telegramBotId(), session.language(), session.stage(), session.disposition(),
                session.ended(), session.messageCount(), session.createdAt(), session.lastMessageAt(),
                session.operatorUserId());
    }
}
