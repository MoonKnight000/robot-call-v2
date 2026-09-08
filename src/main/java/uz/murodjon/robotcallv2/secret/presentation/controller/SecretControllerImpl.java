package uz.murodjon.robotcallv2.secret.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.secret.application.dto.CreateSecretRequest;
import uz.murodjon.robotcallv2.secret.application.dto.SecretRow;
import uz.murodjon.robotcallv2.secret.application.dto.UpdateSecretRequest;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class SecretControllerImpl implements SecretController {

    private final SecretUseCase secretUseCase;

    public SecretControllerImpl(SecretUseCase secretUseCase) {
        this.secretUseCase = secretUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<SecretRow>>> list(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(secretUseCase.findSecrets(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<SecretRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(secretUseCase.findSecret(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<SecretRow>> create(long companyId, CreateSecretRequest request) {
        return ResponseEntity.ok(ResponseData.ok(secretUseCase.createSecret(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<SecretRow>> update(long companyId, long id, UpdateSecretRequest request) {
        return ResponseEntity.ok(ResponseData.ok(secretUseCase.updateSecret(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        secretUseCase.deleteSecret(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
