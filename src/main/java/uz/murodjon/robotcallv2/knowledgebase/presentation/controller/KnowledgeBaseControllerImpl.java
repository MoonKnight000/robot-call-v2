package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeBaseUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class KnowledgeBaseControllerImpl implements KnowledgeBaseController {

    private final KnowledgeBaseUseCase knowledgeBaseUseCase;

    public KnowledgeBaseControllerImpl(KnowledgeBaseUseCase knowledgeBaseUseCase) {
        this.knowledgeBaseUseCase = knowledgeBaseUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeItemResponse>> create(long companyId, KnowledgeItemCreateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeBaseUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeItemResponse>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeBaseUseCase.get(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeItemResponse>> update(long companyId, long id, KnowledgeItemUpdateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeBaseUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        knowledgeBaseUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<KnowledgeItemResponse>>> list(long companyId, KnowledgeItemFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeBaseUseCase.list(companyId, filter)));
    }
}
