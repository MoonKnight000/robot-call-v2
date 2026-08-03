package uz.murodjon.uysotvoice.apikey.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.apikey.dto.ApiKey;
import uz.murodjon.uysotvoice.apikey.dto.ApiKeyFilter;
import uz.murodjon.uysotvoice.apikey.dto.CreateApiKeyRequest;
import uz.murodjon.uysotvoice.apikey.dto.CreateApiKeyResponse;
import uz.murodjon.uysotvoice.apikey.service.ApiKeyService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class ApiKeyControllerImpl implements ApiKeyController {

    private final ApiKeyService service;

    public ApiKeyControllerImpl(ApiKeyService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<CreateApiKeyResponse>> create(CreateApiKeyRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ApiKey>>> list(ApiKeyFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> revoke(long id) {
        service.revoke(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
