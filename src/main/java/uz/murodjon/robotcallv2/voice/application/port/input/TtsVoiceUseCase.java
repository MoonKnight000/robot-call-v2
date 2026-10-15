package uz.murodjon.robotcallv2.voice.application.port.input;

import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

public interface TtsVoiceUseCase {

    /**
     * The shared voices plus the company's own clones. {@code mode} narrows to one engine's
     * voice family; null asks for both.
     */
    List<TtsVoice> findSelectableByCompanyIdAndMode(long companyId, PipelineMode mode, String language);

    TtsVoice find(String id);

    boolean isSelectable(long companyId, PipelineMode mode, String id);

    List<String> findSelectableIds(long companyId, PipelineMode mode);
}
