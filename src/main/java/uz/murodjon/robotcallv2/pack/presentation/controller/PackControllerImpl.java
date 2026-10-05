package uz.murodjon.robotcallv2.pack.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.pack.application.dto.*;
import uz.murodjon.robotcallv2.pack.application.port.input.PackUseCase;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class PackControllerImpl implements PackController {

    private final PackUseCase packUseCase;

    public PackControllerImpl(PackUseCase packUseCase) {
        this.packUseCase = packUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<PackRow>>> list() {
        return ResponseEntity.ok(ResponseData.ok(packUseCase.findPacks()));
    }

    @Override
    public ResponseEntity<ResponseData<PackInstallResponse>> install(long companyId, String key,
                                                                    InstallPackRequest request) {
        return ResponseEntity.ok(ResponseData.ok(packUseCase.installPack(companyId, key, request)));
    }

    @Override
    public ResponseEntity<ResponseData<VerticalPack>> generate(long companyId, GeneratePackRequest request) {
        return ResponseEntity.ok(ResponseData.ok(packUseCase.generatePack(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PackInstallResponse>> installDraft(long companyId,
                                                                         InstallDraftRequest request) {
        return ResponseEntity.ok(ResponseData.ok(packUseCase.installDraft(companyId, request)));
    }
}
