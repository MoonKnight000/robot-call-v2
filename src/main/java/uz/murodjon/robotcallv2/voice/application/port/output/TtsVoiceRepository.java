package uz.murodjon.robotcallv2.voice.application.port.output;

import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

public interface TtsVoiceRepository {

    List<TtsVoice> findAll();

    List<TtsVoice> findByLanguage(String language);

    TtsVoice find(String id);

    List<String> findIds();
}
