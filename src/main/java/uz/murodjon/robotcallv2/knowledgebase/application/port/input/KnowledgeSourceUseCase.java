package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.UpdateKnowledgeSourceRequest;

import java.util.List;

public interface KnowledgeSourceUseCase {

    List<KnowledgeSourceRow> createKnowledgeSources(long companyId, CreateKnowledgeSourceRequest request);

    /** All of the company's sources, or only the given agent's when {@code agentId} is set. */
    List<KnowledgeSourceRow> findKnowledgeSources(long companyId, Long agentId);

    KnowledgeSourceRow findKnowledgeSource(long companyId, long id);

    KnowledgeSourceRow updateKnowledgeSource(long companyId, long id, UpdateKnowledgeSourceRequest request);

    KnowledgeSourceRow retryKnowledgeSource(long companyId, long id);

    void deleteKnowledgeSource(long companyId, long id);
}
