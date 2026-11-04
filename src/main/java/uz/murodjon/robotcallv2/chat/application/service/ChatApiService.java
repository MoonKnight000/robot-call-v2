package uz.murodjon.robotcallv2.chat.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartApiChatRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatApiUseCase;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatTurn;
import uz.murodjon.robotcallv2.chat.domain.entity.StartedChat;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatChannel;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.company.domain.service.CompanyValidator;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Clock;

/** {@link ChatApiUseCase}: the company's server opens and feeds conversations with its API key. */
@Service
public class ChatApiService implements ChatApiUseCase {

    private final ChatConversations conversations;
    private final ChatSessionRepository repository;
    private final AiAgentUseCase aiAgentUseCase;
    private final CompanyUseCase companyUseCase;
    private final Clock clock;

    public ChatApiService(ChatConversations conversations, ChatSessionRepository repository,
                          AiAgentUseCase aiAgentUseCase, CompanyUseCase companyUseCase, Clock clock) {
        this.conversations = conversations;
        this.repository = repository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.companyUseCase = companyUseCase;
        this.clock = clock;
    }

    @Override
    public StartChatResponse startChat(long companyId, StartApiChatRequest request) {
        companyUseCase.requireFeatureEnabled(companyId, Feature.CHAT_AGENT);
        aiAgentUseCase.requireAgentForChat(companyId, request.agentId());
        if (!conversations.modelAvailable()) {
            throw new ConflictException(ErrorCode.CHAT_MODEL_NOT_CONFIGURED);
        }
        String language = request.language() != null && !request.language().isBlank()
                ? CompanyValidator.resolveLanguageCode(request.language())
                : null;
        StartedChat started = conversations.start(ChatSession.startingOnApi(conversations.newSessionKey(), companyId,
                request.agentId(), language, conversations.firstStageOf(companyId, request.agentId()), request.facts()));
        return new StartChatResponse(started.session().sessionKey(), started.greeting(), started.session().language(),
                conversations.maxMessages());
    }

    @Override
    public ChatReplyResponse sendMessage(long companyId, String sessionKey, SendChatMessageRequest request) {
        ChatSession session = requireApiSession(companyId, sessionKey);
        conversations.requireOpen(session);
        ChatTurn turn = conversations.answer(session, request.text().trim());
        return new ChatReplyResponse(turn.reply(), turn.session().ended(), conversations.messagesLeft(turn.session()),
                turn.session().isTakenOver());
    }

    @Override
    public ChatReplyResponse endChat(long companyId, String sessionKey) {
        ChatSession session = requireApiSession(companyId, sessionKey);
        ChatSession ended = session.ended() ? session : repository.save(session.afterTurn(session.stage(),
                session.outcome(), session.disposition(), true, 0, clock.instant()));
        return new ChatReplyResponse(null, true, 0, ended.isTakenOver());
    }

    /** This company's API conversation; anything else is, to the caller, one that does not exist. */
    private ChatSession requireApiSession(long companyId, String sessionKey) {
        ChatSession session = repository.findBySessionKey(sessionKey);
        if (session == null || session.companyId() != companyId || session.channel() != ChatChannel.API) {
            throw new NotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND, sessionKey);
        }
        return session;
    }
}
