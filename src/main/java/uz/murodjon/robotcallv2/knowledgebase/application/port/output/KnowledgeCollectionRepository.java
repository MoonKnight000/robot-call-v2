package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeCollection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeCollectionRepository {

    /** Inserts when {@code id} is 0, otherwise renames. */
    KnowledgeCollection save(KnowledgeCollection collection);

    Optional<KnowledgeCollection> findByCompanyIdAndId(long companyId, long id);

    Optional<KnowledgeCollection> findByCompanyIdAndName(long companyId, String name);

    List<KnowledgeCollection> findByCompanyId(long companyId);

    /** Removes the collection with its sources and attachments. */
    void deleteByCompanyIdAndId(long companyId, long id);

    /** Collection id → how many sources it holds, for the company's collections. */
    Map<Long, Integer> countSourcesByCompanyId(long companyId);

    /** Collection id → the agents attached to it, for the company's collections. */
    Map<Long, List<Long>> findAgentIdsByCompanyId(long companyId);

    /** Makes exactly {@code agentIds} the agents attached to the collection. */
    void replaceAgents(long collectionId, Collection<Long> agentIds);
}
