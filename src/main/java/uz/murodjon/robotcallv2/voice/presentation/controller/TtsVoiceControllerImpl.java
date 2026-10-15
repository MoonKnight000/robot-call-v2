package uz.murodjon.robotcallv2.voice.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.voice.application.dto.CloneVoiceRequest;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.application.port.input.VoiceCloneUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

@RestController
public class TtsVoiceControllerImpl implements TtsVoiceController {

    private final TtsVoiceUseCase ttsVoiceUseCase;
    private final VoiceCloneUseCase voiceCloneUseCase;

    public TtsVoiceControllerImpl(TtsVoiceUseCase ttsVoiceUseCase, VoiceCloneUseCase voiceCloneUseCase) {
        this.ttsVoiceUseCase = ttsVoiceUseCase;
        this.voiceCloneUseCase = voiceCloneUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<TtsVoice>>> voices(long companyId, PipelineMode mode, String language) {
        return ResponseEntity.ok(ResponseData.ok(ttsVoiceUseCase.findSelectableByCompanyIdAndMode(companyId, mode, language)));
    }

    @Override
    public ResponseEntity<ResponseData<TtsVoice>> cloneVoice(long companyId, CloneVoiceRequest request,
                                                             List<MultipartFile> files) {
        return ResponseEntity.ok(ResponseData.ok(voiceCloneUseCase.cloneVoice(companyId, request, UploadedFile.fromAll(files))));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteClonedVoice(long companyId, String id) {
        voiceCloneUseCase.deleteClonedVoice(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
