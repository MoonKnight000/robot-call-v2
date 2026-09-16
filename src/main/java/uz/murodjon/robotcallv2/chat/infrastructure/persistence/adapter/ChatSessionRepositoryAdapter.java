package uz.murodjon.robotcallv2.chat.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatFilter;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatChannel;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity.ChatSessionEntity;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.mapper.ChatEntityMapper;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.repository.ChatMessageJpaRepository;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.repository.ChatSessionJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChatSessionRepositoryAdapter implements ChatSessionRepository {

    private final ChatSessionJpaRepository sessionJpaRepository;
    private final ChatMessageJpaRepository messageJpaRepository;
    private final ChatEntityMapper mapper;

    public ChatSessionRepositoryAdapter(ChatSessionJpaRepository sessionJpaRepository,
                                        ChatMessageJpaRepository messageJpaRepository,
                                        ChatEntityMapper mapper) {
        this.sessionJpaRepository = sessionJpaRepository;
        this.messageJpaRepository = messageJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ChatSession save(ChatSession session) {
        ChatSessionEntity entity = session.id() == null
                ? new ChatSessionEntity()
                : sessionJpaRepository.findById(session.id()).orElseGet(ChatSessionEntity::new);
        return mapper.toChatSession(sessionJpaRepository.save(mapper.toChatSessionEntity(session, entity)));
    }

    @Override
    public ChatSession findBySessionKey(String sessionKey) {
        return sessionJpaRepository.findBySessionKey(sessionKey).map(mapper::toChatSession).orElse(null);
    }

    @Override
    public ChatSession findOpenByTelegram(long telegramBotId, String telegramChatId) {
        return sessionJpaRepository
                .findByTelegramBotIdAndTelegramChatIdAndEndedFalse(telegramBotId, telegramChatId)
                .map(mapper::toChatSession)
                .orElse(null);
    }

    @Override
    public ChatSession findByCompanyIdAndId(long companyId, long id) {
        return sessionJpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toChatSession).orElse(null);
    }

    @Override
    public List<ChatSession> findAll(long companyId, ChatFilter filter) {
        return sessionJpaRepository.findAll(matching(companyId, filter), filter.pageable()).stream()
                .map(mapper::toChatSession)
                .toList();
    }

    @Override
    public long count(long companyId, ChatFilter filter) {
        return sessionJpaRepository.count(matching(companyId, filter));
    }

    @Override
    public void addMessage(ChatMessage message) {
        messageJpaRepository.save(mapper.toChatMessageEntity(message));
    }

    @Override
    public List<ChatMessage> findMessages(long chatSessionId) {
        return messageJpaRepository.findByChatSessionIdOrderByIdAsc(chatSessionId)
                .stream()
                .map(mapper::toChatMessage)
                .toList();
    }

    /**
     * The messages go first and by id, rather than leaving it to the foreign key's cascade:
     * a batch of conversations with a long transcript each is a lot of rows to delete inside
     * one statement nobody chose the size of.
     */
    @Override
    @Transactional
    public int deleteSessionsCreatedBefore(Instant cutoff, int batchSize) {
        List<ChatSessionEntity> expired =
                sessionJpaRepository.findByCreatedAtLessThanOrderByIdAsc(cutoff, Limit.of(batchSize));
        if (expired.isEmpty()) {
            return 0;
        }
        messageJpaRepository.deleteByChatSessionIds(expired.stream().map(ChatSessionEntity::getId).toList());
        sessionJpaRepository.deleteAll(expired);
        return expired.size();
    }

    private static Specification<ChatSessionEntity> matching(long companyId, ChatFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.aiAgentId() != null) {
                predicates.add(cb.equal(root.get("aiAgentId"), filter.aiAgentId()));
            }
            if (filter.channel() == ChatChannel.TELEGRAM) {
                predicates.add(cb.isNotNull(root.get("telegramBotId")));
            } else if (filter.channel() == ChatChannel.WIDGET) {
                predicates.add(cb.isNull(root.get("telegramBotId")));
            }
            if (filter.ended() != null) {
                predicates.add(cb.equal(root.get("ended"), filter.ended()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
