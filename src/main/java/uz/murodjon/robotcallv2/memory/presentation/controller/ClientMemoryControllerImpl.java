package uz.murodjon.robotcallv2.memory.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateRememberedCallRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpsertClientMemoryFactRequest;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ClientMemoryControllerImpl implements ClientMemoryController {

    private final ClientMemoryUseCase clientMemoryUseCase;

    public ClientMemoryControllerImpl(ClientMemoryUseCase clientMemoryUseCase) {
        this.clientMemoryUseCase = clientMemoryUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<ClientMemory>> get(long companyId, String phone) {
        return ResponseEntity.ok(ResponseData.ok(clientMemoryUseCase.requireByCompanyIdAndPhone(companyId, phone)));
    }

    @Override
    public ResponseEntity<ResponseData<ClientMemory>> update(long companyId, String phone, UpdateClientMemoryRequest request) {
        return ResponseEntity.ok(ResponseData.ok(
                clientMemoryUseCase.updateByCompanyIdAndPhone(companyId, phone, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, String phone) {
        clientMemoryUseCase.deleteByCompanyIdAndPhone(companyId, phone);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<ClientMemory>> upsertFact(long companyId, String phone, String key,
                                                                 UpsertClientMemoryFactRequest request) {
        return ResponseEntity.ok(ResponseData.ok(
                clientMemoryUseCase.upsertFactByCompanyIdAndPhone(companyId, phone, key, request)));
    }

    @Override
    public ResponseEntity<ResponseData<ClientMemory>> deleteFact(long companyId, String phone, String key) {
        return ResponseEntity.ok(ResponseData.ok(clientMemoryUseCase.deleteFactByCompanyIdAndPhone(companyId, phone, key)));
    }

    @Override
    public ResponseEntity<ResponseData<ClientMemory>> updateCall(long companyId, String phone, String callId,
                                                                 UpdateRememberedCallRequest request) {
        return ResponseEntity.ok(ResponseData.ok(
                clientMemoryUseCase.updateCallByCompanyIdAndPhone(companyId, phone, callId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<ClientMemory>> deleteCall(long companyId, String phone, String callId) {
        return ResponseEntity.ok(ResponseData.ok(clientMemoryUseCase.deleteCallByCompanyIdAndPhone(companyId, phone, callId)));
    }
}
