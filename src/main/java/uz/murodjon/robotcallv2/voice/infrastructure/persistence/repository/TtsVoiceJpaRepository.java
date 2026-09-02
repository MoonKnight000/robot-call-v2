package uz.murodjon.robotcallv2.voice.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;

import java.util.List;

@Repository
public interface TtsVoiceJpaRepository extends JpaRepository<TtsVoiceEntity, String> {

    List<TtsVoiceEntity> findAllByOrderByIdAsc();
}
