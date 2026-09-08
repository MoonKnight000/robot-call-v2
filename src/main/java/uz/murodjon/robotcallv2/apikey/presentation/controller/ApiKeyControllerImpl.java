package uz.murodjon.robotcallv2.apikey.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.apikey.application.dto.ApiKeyRow;
import uz.murodjon.robotcallv2.apikey.application.dto.CreateApiKeyRequest;
import uz.murodjon.robotcallv2.apikey.application.dto.CreatedApiKeyResponse;
import uz.murodjon.robotcallv2.apikey.application.port.input.ApiKeyUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;
import java.util.Set;

@RestController
public class ApiKeyControllerImpl implements ApiKeyController {

    private final ApiKeyUseCase apiKeyUseCase;

    public ApiKeyControllerImpl(ApiKeyUseCase apiKeyUseCase) {
        this.apiKeyUseCase = apiKeyUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<Set<Permission>>> grantableScopes() {
        return ResponseEntity.ok(ResponseData.ok(apiKeyUseCase.findGrantableScopes()));
    }

    @Override
    public ResponseEntity<ResponseData<List<ApiKeyRow>>> apiKeys(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(apiKeyUseCase.findApiKeys(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<CreatedApiKeyResponse>> createApiKey(long companyId,
                                                                           CreateApiKeyRequest request) {
        return ResponseEntity.ok(ResponseData.ok(apiKeyUseCase.createApiKey(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> revokeApiKey(long companyId, long id) {
        apiKeyUseCase.revokeApiKey(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
