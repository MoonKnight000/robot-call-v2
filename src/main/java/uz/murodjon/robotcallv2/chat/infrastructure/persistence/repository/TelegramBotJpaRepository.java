package uz.murodjon.robotcallv2.chat.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity.TelegramBotEntity;

import java.util.List;
import java.util.Optional;

public interface TelegramBotJpaRepository extends JpaRepository<TelegramBotEntity, Long> {

    Optional<TelegramBotEntity> findByCompanyIdAndId(Long companyId, Long id);

    List<TelegramBotEntity> findByCompanyIdOrderByIdAsc(Long companyId);

    void deleteByCompanyIdAndId(Long companyId, Long id);
}
