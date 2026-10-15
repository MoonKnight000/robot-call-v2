package uz.murodjon.robotcallv2.voice.application.port.input;

import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.voice.application.dto.CloneVoiceRequest;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

/** A company's own voices, cloned from recordings of a person who agreed to it. */
public interface VoiceCloneUseCase {

    TtsVoice cloneVoice(long companyId, CloneVoiceRequest request, List<UploadedFile> samples);

    void deleteClonedVoice(long companyId, String voiceId);
}
