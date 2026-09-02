package uz.murodjon.robotcallv2.voice.application.port.input;

import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

public interface TtsVoiceUseCase {

    List<TtsVoice> findSelectable(String language);

    List<TtsVoice> findSelectableForCurrentCompany(String language);

    TtsVoice find(String id);

    boolean isSelectable(String id);

    List<String> selectableIds();
}
