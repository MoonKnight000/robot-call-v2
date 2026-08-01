package uz.murodjon.uysotvoice.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.voice.entity.TtsVoice;

import java.util.List;

public interface TtsVoiceJpaRepository extends JpaRepository<TtsVoice, String> {

    List<TtsVoice> findAllByOrderByIdAsc();
}
