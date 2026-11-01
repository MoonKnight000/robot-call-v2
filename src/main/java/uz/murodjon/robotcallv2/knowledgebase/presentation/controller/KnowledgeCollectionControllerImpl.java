package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeCollectionRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.SaveKnowledgeCollectionRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeCollectionUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class KnowledgeCollectionControllerImpl implements KnowledgeCollectionController {

    private final KnowledgeCollectionUseCase knowledgeCollectionUseCase;

    public KnowledgeCollectionControllerImpl(KnowledgeCollectionUseCase knowledgeCollectionUseCase) {
        this.knowledgeCollectionUseCase = knowledgeCollectionUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeCollectionRow>> create(long companyId,
                                                                       SaveKnowledgeCollectionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeCollectionUseCase.createKnowledgeCollection(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<KnowledgeCollectionRow>>> list(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeCollectionUseCase.findKnowledgeCollections(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<KnowledgeSourceRow>>> listSources(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeCollectionUseCase.findKnowledgeCollectionSources(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeCollectionRow>> update(long companyId, long id,
                                                                       SaveKnowledgeCollectionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeCollectionUseCase.updateKnowledgeCollection(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        knowledgeCollectionUseCase.deleteKnowledgeCollection(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
