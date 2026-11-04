package uz.murodjon.robotcallv2.chat.infrastructure.persistence.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity.ChatSessionEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, Long>,
        JpaSpecificationExecutor<ChatSessionEntity> {

    Optional<ChatSessionEntity> findBySessionKey(String sessionKey);

    Optional<ChatSessionEntity> findByCompanyIdAndId(Long companyId, Long id);

    /** At most one by the partial unique index in V17: one open conversation per Telegram chat. */
    Optional<ChatSessionEntity> findByTelegramBotIdAndTelegramChatIdAndEndedFalse(Long telegramBotId,
                                                                                 String telegramChatId);

    /** Retention takes conversations in batches, oldest first, so one sweep cannot lock the table. */
    List<ChatSessionEntity> findByCreatedAtLessThanOrderByIdAsc(Instant cutoff, Limit limit);

    @Query("""
            select s from ChatSessionEntity s
            where s.analyzedAt is null and (s.ended = true or s.lastMessageAt < :silentBefore)
            order by s.lastMessageAt asc""")
    List<ChatSessionEntity> findToAnalyze(@Param("silentBefore") Instant silentBefore, Limit limit);
}
