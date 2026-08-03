package uz.murodjon.uysotvoice.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.voice.entity.TtsVoiceEntity;

import java.util.List;

public interface TtsVoiceJpaRepository extends JpaRepository<TtsVoiceEntity, String> {

    List<TtsVoiceEntity> findAllByOrderByIdAsc();
}
