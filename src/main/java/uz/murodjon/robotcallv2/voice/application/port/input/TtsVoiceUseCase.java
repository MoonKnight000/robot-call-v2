package uz.murodjon.robotcallv2.voice.application.port.input;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

public interface TtsVoiceUseCase {

    /** {@code mode} narrows to one engine's voice family; null asks for both. */
    List<TtsVoice> findSelectableByMode(PipelineMode mode, String language);

    TtsVoice find(String id);

    boolean isSelectable(PipelineMode mode, String id);

    List<String> findSelectableIds(PipelineMode mode);
}
