package uz.murodjon.robotcallv2.missedcall.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.missedcall.application.dto.AssignMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.application.dto.MissedCallCallBackResponse;
import uz.murodjon.robotcallv2.missedcall.application.dto.UpdateMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class MissedCallControllerImpl implements MissedCallController {

    private final MissedCallUseCase missedCallUseCase;

    public MissedCallControllerImpl(MissedCallUseCase missedCallUseCase) {
        this.missedCallUseCase = missedCallUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<MissedCall>>> list(long companyId, MissedCallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(missedCallUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<MissedCall>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(missedCallUseCase.requireMissedCall(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<MissedCall>> update(long companyId, long id, UpdateMissedCallRequest request) {
        return ResponseEntity.ok(ResponseData.ok(missedCallUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<MissedCall>> assign(long companyId, long id, AssignMissedCallRequest request) {
        return ResponseEntity.ok(ResponseData.ok(missedCallUseCase.assign(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<MissedCallCallBackResponse>> callBack(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(missedCallUseCase.callBack(companyId, id)));
    }
}
