package uz.murodjon.robotcallv2.dialer.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.dialer.application.dto.PreviewCardResponse;
import uz.murodjon.robotcallv2.dialer.application.dto.PreviewDialResponse;
import uz.murodjon.robotcallv2.dialer.application.port.input.PreviewDialUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class PreviewDialControllerImpl implements PreviewDialController {

    private final PreviewDialUseCase previewDialUseCase;

    public PreviewDialControllerImpl(PreviewDialUseCase previewDialUseCase) {
        this.previewDialUseCase = previewDialUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PreviewCardResponse>> next(long companyId, long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(previewDialUseCase.findNextCard(companyId, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<PreviewDialResponse>> dial(long companyId, long campaignId, long targetId) {
        return ResponseEntity.ok(ResponseData.ok(previewDialUseCase.callTarget(companyId, campaignId, targetId)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> skip(long companyId, long campaignId, long targetId) {
        previewDialUseCase.releaseTarget(companyId, campaignId, targetId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
