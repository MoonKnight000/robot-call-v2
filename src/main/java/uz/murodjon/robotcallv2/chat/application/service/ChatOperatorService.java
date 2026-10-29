package uz.murodjon.robotcallv2.chat.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.OperatorChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatHistoryUseCase;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatOperatorUseCase;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

/**
 * Operator takeover of widget and Telegram conversations (docs/api/chat.md). Another user may
 * take a conversation over from a colleague — a supervisor stepping in is the point — and the
 * audit trail says who did.
 */
@Service
public class ChatOperatorService implements ChatOperatorUseCase {

    private final ChatSessionRepository repository;
    private final ChatConversations conversations;
    private final TelegramService telegramService;
    private final ChatHistoryUseCase chatHistoryUseCase;
    private final CurrentUser currentUser;
    private final AuditUseCase auditUseCase;

    public ChatOperatorService(ChatSessionRepository repository, ChatConversations conversations,
                               TelegramService telegramService, ChatHistoryUseCase chatHistoryUseCase,
                               CurrentUser currentUser, AuditUseCase auditUseCase) {
        this.repository = repository;
        this.conversations = conversations;
        this.telegramService = telegramService;
        this.chatHistoryUseCase = chatHistoryUseCase;
        this.currentUser = currentUser;
        this.auditUseCase = auditUseCase;
    }

    @Override
    public ChatDetail takeoverChat(long companyId, long chatId) {
        ChatSession session = requireSession(companyId, chatId);
        if (session.ended()) {
            throw new ConflictException(ErrorCode.CHAT_SESSION_ENDED, chatId);
        }
        long userId = currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.CHAT_OPERATOR_USER_REQUIRED));
        repository.save(session.withOperator(userId));
        auditUseCase.record(companyId, "CHAT_TAKEOVER", "chat_session", String.valueOf(chatId), currentUser.actorName());
        return chatHistoryUseCase.requireChat(companyId, chatId);
    }

    @Override
    public ChatDetail releaseChat(long companyId, long chatId) {
        ChatSession session = requireSession(companyId, chatId);
        if (session.isTakenOver()) {
            repository.save(session.withOperator(null));
            auditUseCase.record(companyId, "CHAT_RELEASE", "chat_session", String.valueOf(chatId), currentUser.actorName());
        }
        return chatHistoryUseCase.requireChat(companyId, chatId);
    }

    @Override
    public ChatDetail sendOperatorMessage(long companyId, long chatId, OperatorChatMessageRequest request) {
        ChatSession session = requireSession(companyId, chatId);
        if (!session.isTakenOver()) {
            throw new ConflictException(ErrorCode.CHAT_NOT_TAKEN_OVER, chatId);
        }
        if (session.ended()) {
            throw new ConflictException(ErrorCode.CHAT_SESSION_ENDED, chatId);
        }
        String text = request.text().trim();
        // Sent before it is stored: a line Telegram refused must not sit in the transcript as said.
        if (session.telegramBotId() != null) {
            telegramService.deliverOperatorMessage(session, text);
        }
        conversations.writeAsOperator(session, text);
        return chatHistoryUseCase.requireChat(companyId, chatId);
    }

    private ChatSession requireSession(long companyId, long chatId) {
        ChatSession session = repository.findByCompanyIdAndId(companyId, chatId);
        if (session == null) {
            throw new NotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND, chatId);
        }
        return session;
    }
}
