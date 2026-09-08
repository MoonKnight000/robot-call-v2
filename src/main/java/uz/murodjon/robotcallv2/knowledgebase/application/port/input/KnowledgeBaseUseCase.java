package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface KnowledgeBaseUseCase {

    KnowledgeItemResponse create(long companyId, KnowledgeItemCreateRequest request);

    KnowledgeItemResponse get(long companyId, long id);

    KnowledgeItemResponse update(long companyId, long id, KnowledgeItemUpdateRequest request);

    void delete(long companyId, long id);

    PageableData<KnowledgeItemResponse> list(long companyId, KnowledgeItemFilter filter);

    /** The best matching answer for a caller question, narrowed to what this agent may say. */
    String findRelevantAnswer(long companyId, Long agentId, String query, String language);
}
