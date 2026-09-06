package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeBaseUseCase;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class KnowledgeBaseControllerImpl implements KnowledgeBaseController {

    private final KnowledgeBaseUseCase knowledgeBaseUseCase;

    public KnowledgeBaseControllerImpl(KnowledgeBaseUseCase knowledgeBaseUseCase) {
        this.knowledgeBaseUseCase = knowledgeBaseUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeItemResponse>> create(KnowledgeItemCreateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeBaseUseCase.create(request)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeItemResponse>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeBaseUseCase.get(id)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeItemResponse>> update(long id, KnowledgeItemUpdateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeBaseUseCase.update(id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long id) {
        knowledgeBaseUseCase.delete(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<KnowledgeItemResponse>>> list(KnowledgeItemFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeBaseUseCase.list(filter)));
    }
}
