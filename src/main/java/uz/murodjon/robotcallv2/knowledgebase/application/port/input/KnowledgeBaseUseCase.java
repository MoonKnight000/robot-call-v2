package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

/**
 * The curated question-and-answer items. Which item answers a caller is chosen during the call by
 * {@link KnowledgeRetrievalUseCase#findCuratedAnswer}.
 */
public interface KnowledgeBaseUseCase {

    KnowledgeItemResponse create(long companyId, KnowledgeItemCreateRequest request);

    KnowledgeItemResponse findItem(long companyId, long id);

    KnowledgeItemResponse update(long companyId, long id, KnowledgeItemUpdateRequest request);

    void delete(long companyId, long id);

    PageableData<KnowledgeItemResponse> list(long companyId, KnowledgeItemFilter filter);

    /** The topics the company's items use, alphabetically — what the topic picker offers besides typing a new one. */
    List<String> findTopics(long companyId);
}
