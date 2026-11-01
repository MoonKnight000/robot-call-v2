package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeCollectionRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.SaveKnowledgeCollectionRequest;

import java.util.List;

public interface KnowledgeCollectionUseCase {

    KnowledgeCollectionRow createKnowledgeCollection(long companyId, SaveKnowledgeCollectionRequest request);

    List<KnowledgeCollectionRow> findKnowledgeCollections(long companyId);

    List<KnowledgeSourceRow> findKnowledgeCollectionSources(long companyId, long id);

    /** Renames the collection and, when {@code agentIds} is given, replaces the agents attached to it. */
    KnowledgeCollectionRow updateKnowledgeCollection(long companyId, long id, SaveKnowledgeCollectionRequest request);

    /** Deletes the collection together with its sources. */
    void deleteKnowledgeCollection(long companyId, long id);
}
