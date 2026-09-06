package uz.murodjon.robotcallv2.memory.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
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
    public ResponseEntity<ResponseData<ClientMemory>> get(String phone) {
        return ResponseEntity.ok(ResponseData.ok(clientMemoryUseCase.findForCurrentCompany(phone)));
    }

    @Override
    public ResponseEntity<ResponseData<ClientMemory>> update(String phone, UpdateClientMemoryRequest request) {
        return ResponseEntity.ok(ResponseData.ok(clientMemoryUseCase.updateForCurrentCompany(phone, request)));
    }
}
