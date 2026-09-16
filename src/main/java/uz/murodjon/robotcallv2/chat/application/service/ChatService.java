package uz.murodjon.robotcallv2.chat.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatUseCase;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatTurn;
import uz.murodjon.robotcallv2.chat.domain.entity.StartedChat;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.widget.application.port.input.AgentWidgetUseCase;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTarget;

import java.time.Instant;

/**
 * The widget's text channel (UNIVERSAL-OPERATOR-PLAN E.4): the same agent the button on that
 * page would have called, answering in writing instead.
 *
 * <p>This class is only the channel — who is allowed to write, and what the browser gets
 * back. The conversation itself is {@link ChatConversations}, which the Telegram bot uses
 * too, so neither channel can drift into being a different agent.
 */
@Service
public class ChatService implements ChatUseCase {

    private final ChatConversations conversations;
    private final ChatSessionRepository repository;
    private final AgentWidgetUseCase widgetUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final AuditUseCase auditUseCase;

    public ChatService(ChatConversations conversations, ChatSessionRepository repository,
                       AgentWidgetUseCase widgetUseCase, CompanyConfigUseCase companyConfigUseCase,
                       AuditUseCase auditUseCase) {
        this.conversations = conversations;
        this.repository = repository;
        this.widgetUseCase = widgetUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.auditUseCase = auditUseCase;
    }

    @Override
    public StartChatResponse startChat(String widgetKey, String origin, String language) {
        WidgetTarget target = widgetUseCase.findPublicTarget(widgetKey, origin);
        if (!conversations.modelAvailable()) {
            throw new ConflictException(ErrorCode.CHAT_MODEL_NOT_CONFIGURED);
        }
        String resolvedLanguage = language != null && !language.isBlank()
                ? companyConfigUseCase.resolveLanguage(target.companyId(), language)
                : null;
        StartedChat started = conversations.start(ChatSession.startingOnWidget(conversations.newSessionKey(),
                target.companyId(), target.agentId(), target.widgetId(), resolvedLanguage,
                conversations.firstStageOf(target.companyId(), target.agentId())));
        auditUseCase.record(target.companyId(), "CHAT_START", "agent_widget",
                String.valueOf(target.widgetId()), origin);
        return new StartChatResponse(started.session().sessionKey(), started.greeting(),
                started.session().language(), conversations.maxMessages());
    }

    @Override
    public ChatReplyResponse sendMessage(String widgetKey, String origin, String sessionKey,
                                         SendChatMessageRequest request) {
        WidgetTarget target = widgetUseCase.findPublicTarget(widgetKey, origin);
        ChatSession session = repository.findBySessionKey(sessionKey);
        // A conversation of another widget's agent is, to this request, one that does not exist.
        if (session == null || session.companyId() != target.companyId()
                || session.aiAgentId() != target.agentId()) {
            throw new NotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND, sessionKey);
        }
        conversations.requireOpen(session);
        ChatTurn turn = conversations.answer(session, request.text().trim());
        return new ChatReplyResponse(turn.reply(), turn.session().ended(),
                conversations.messagesLeft(turn.session()));
    }

    @Override
    @Transactional
    public int deleteSessionsCreatedBefore(Instant cutoff, int batchSize) {
        return repository.deleteSessionsCreatedBefore(cutoff, batchSize);
    }
}
