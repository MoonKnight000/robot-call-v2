package uz.murodjon.robotcallv2.voicemail.presentation.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.FileResponseFactory;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voicemail.application.dto.SaveVoicemailBoxRequest;
import uz.murodjon.robotcallv2.voicemail.application.dto.UpdateVoicemailStatusRequest;
import uz.murodjon.robotcallv2.voicemail.application.port.input.VoicemailUseCase;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBoxFilter;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessageFilter;

@RestController
public class VoicemailControllerImpl implements VoicemailController {

    private final VoicemailUseCase voicemailUseCase;
    private final FileResponseFactory fileResponseFactory;

    public VoicemailControllerImpl(VoicemailUseCase voicemailUseCase, FileResponseFactory fileResponseFactory) {
        this.voicemailUseCase = voicemailUseCase;
        this.fileResponseFactory = fileResponseFactory;
    }

    @Override
    public ResponseEntity<ResponseData<VoicemailBox>> createBox(long companyId, SaveVoicemailBoxRequest request) {
        return ResponseEntity.ok(ResponseData.ok(voicemailUseCase.createBox(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<VoicemailBox>>> listBoxes(long companyId, VoicemailBoxFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(voicemailUseCase.listBoxes(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<VoicemailBox>> getBox(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(voicemailUseCase.requireBox(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<VoicemailBox>> updateBox(long companyId, long id, SaveVoicemailBoxRequest request) {
        return ResponseEntity.ok(ResponseData.ok(voicemailUseCase.updateBox(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteBox(long companyId, long id) {
        voicemailUseCase.deleteBox(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<VoicemailMessage>>> listMessages(long companyId,
                                                                                     VoicemailMessageFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(voicemailUseCase.listMessages(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<VoicemailMessage>> updateStatus(long companyId, long id,
                                                                       UpdateVoicemailStatusRequest request) {
        return ResponseEntity.ok(ResponseData.ok(voicemailUseCase.updateMessageStatus(companyId, id, request)));
    }

    @Override
    public ResponseEntity<Resource> audio(long companyId, long id, String range) {
        return fileResponseFactory.toResponse(voicemailUseCase.downloadAudio(companyId, id), range);
    }
}
