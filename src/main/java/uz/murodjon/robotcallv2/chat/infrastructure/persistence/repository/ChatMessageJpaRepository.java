package uz.murodjon.robotcallv2.chat.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity.ChatMessageEntity;

import java.util.Collection;
import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    /** The transcript in the order it was written — the id is the order. */
    List<ChatMessageEntity> findByChatSessionIdOrderByIdAsc(Long chatSessionId);

    @Modifying
    @Query("DELETE FROM ChatMessageEntity m WHERE m.chatSessionId IN :sessionIds")
    int deleteByChatSessionIds(@Param("sessionIds") Collection<Long> sessionIds);
}
