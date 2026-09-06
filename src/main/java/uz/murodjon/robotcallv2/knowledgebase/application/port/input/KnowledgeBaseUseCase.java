package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface KnowledgeBaseUseCase {

    KnowledgeItemResponse create(KnowledgeItemCreateRequest request);

    KnowledgeItemResponse get(long id);

    KnowledgeItemResponse update(long id, KnowledgeItemUpdateRequest request);

    void delete(long id);

    PageableData<KnowledgeItemResponse> list(KnowledgeItemFilter filter);

    String findRelevantAnswer(long companyId, String query, String language);
}
