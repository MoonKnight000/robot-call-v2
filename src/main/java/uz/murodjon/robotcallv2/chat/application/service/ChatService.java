package uz.murodjon.robotcallv2.chat.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.ChatUpdatesResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;
import uz.murodjon.robotcallv2.chat.application.dto.WidgetChatMessage;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatUseCase;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatTurn;
import uz.murodjon.robotcallv2.chat.domain.entity.StartedChat;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.widget.application.port.input.AgentWidgetUseCase;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTarget;

import java.time.Instant;
import java.util.List;

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
    private final CompanyUseCase companyUseCase;

    public ChatService(ChatConversations conversations, ChatSessionRepository repository,
                       AgentWidgetUseCase widgetUseCase, CompanyConfigUseCase companyConfigUseCase, CompanyUseCase companyUseCase,
                       AuditUseCase auditUseCase) {
        this.conversations = conversations;
        this.repository = repository;
        this.widgetUseCase = widgetUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.auditUseCase = auditUseCase;
        this.companyUseCase = companyUseCase;
    }

    @Override
    public StartChatResponse startChat(String widgetKey, String origin, String language) {
        WidgetTarget target = widgetUseCase.findPublicTarget(widgetKey, origin);
        companyUseCase.requireFeatureEnabled(target.companyId(), Feature.CHAT_AGENT);
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
        ChatSession session = requireWidgetSession(widgetKey, origin, sessionKey);
        conversations.requireOpen(session);
        ChatTurn turn = conversations.answer(session, request.text().trim());
        return new ChatReplyResponse(turn.reply(), turn.session().ended(),
                conversations.messagesLeft(turn.session()), turn.session().isTakenOver());
    }

    @Override
    public ChatUpdatesResponse findNewMessages(String widgetKey, String origin, String sessionKey, long afterId) {
        ChatSession session = requireWidgetSession(widgetKey, origin, sessionKey);
        List<WidgetChatMessage> messages = repository.findMessages(session.id()).stream()
                .filter(message -> message.id() != null && message.id() > afterId && message.role() != ChatRole.CLIENT)
                .map(message -> new WidgetChatMessage(message.id(), message.role(), message.text(), message.createdAt()))
                .toList();
        return new ChatUpdatesResponse(messages, session.isTakenOver(), session.ended());
    }

    /** This widget's conversation; another widget's is, to the request, one that does not exist. */
    private ChatSession requireWidgetSession(String widgetKey, String origin, String sessionKey) {
        WidgetTarget target = widgetUseCase.findPublicTarget(widgetKey, origin);
        companyUseCase.requireFeatureEnabled(target.companyId(), Feature.CHAT_AGENT);
        ChatSession session = repository.findBySessionKey(sessionKey);
        if (session == null || session.companyId() != target.companyId()
                || session.aiAgentId() != target.agentId()) {
            throw new NotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND, sessionKey);
        }
        return session;
    }

    @Override
    @Transactional
    public int deleteSessionsCreatedBefore(Instant cutoff, int batchSize) {
        return repository.deleteSessionsCreatedBefore(cutoff, batchSize);
    }
}
