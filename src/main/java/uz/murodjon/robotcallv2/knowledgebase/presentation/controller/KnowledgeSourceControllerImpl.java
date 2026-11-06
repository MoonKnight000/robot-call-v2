package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeChunkRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.UpdateKnowledgeChunkRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.UpdateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeChunkUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeSourceUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSourcePage;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class KnowledgeSourceControllerImpl implements KnowledgeSourceController {

    private final KnowledgeSourceUseCase knowledgeSourceUseCase;
    private final KnowledgeChunkUseCase knowledgeChunkUseCase;

    public KnowledgeSourceControllerImpl(KnowledgeSourceUseCase knowledgeSourceUseCase,
                                         KnowledgeChunkUseCase knowledgeChunkUseCase) {
        this.knowledgeSourceUseCase = knowledgeSourceUseCase;
        this.knowledgeChunkUseCase = knowledgeChunkUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<KnowledgeSourceRow>>> create(long companyId,
                                                                         CreateKnowledgeSourceRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeSourceUseCase.createKnowledgeSources(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<KnowledgeSourceRow>>> list(long companyId, Long agentId) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeSourceUseCase.findKnowledgeSources(companyId, agentId)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeSourceRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeSourceUseCase.findKnowledgeSource(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeSourceRow>> update(long companyId, long id,
                                                                   UpdateKnowledgeSourceRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeSourceUseCase.updateKnowledgeSource(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeSourceRow>> retry(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeSourceUseCase.retryKnowledgeSource(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        knowledgeSourceUseCase.deleteKnowledgeSource(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<KnowledgeChunkRow>>> chunks(long companyId, long id, String search,
                                                                               String pageUrl, int page, int size) {
        return ResponseEntity.ok(ResponseData.ok(
                knowledgeChunkUseCase.findChunks(companyId, id, search, pageUrl, page, size)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeChunkRow>> updateChunk(long companyId, long id, long chunkId,
                                                                       UpdateKnowledgeChunkRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeChunkUseCase.updateChunk(companyId, id, chunkId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<KnowledgeSourcePage>>> pages(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeChunkUseCase.findSourcePages(companyId, id)));
    }
}
