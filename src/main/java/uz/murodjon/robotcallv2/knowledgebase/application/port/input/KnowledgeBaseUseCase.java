package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface KnowledgeBaseUseCase {

    KnowledgeItemResponse create(long companyId, KnowledgeItemCreateRequest request);

    KnowledgeItemResponse findItem(long companyId, long id);

    KnowledgeItemResponse update(long companyId, long id, KnowledgeItemUpdateRequest request);

    void delete(long companyId, long id);

    PageableData<KnowledgeItemResponse> list(long companyId, KnowledgeItemFilter filter);

    /** The best matching answer for a caller question, narrowed to what this agent may say. */
    String findRelevantAnswer(long companyId, Long agentId, String query, String language);

    /** The active items this agent may answer from: its own plus the company-wide ones. */
    List<KnowledgeItem> findAllActiveByCompanyIdAndAgentId(long companyId, Long agentId);
}
