package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.IndexedChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;

import java.util.List;

public interface KnowledgeChunkRepository {

    /** Replaces every chunk of the source in one transaction — re-indexing is not additive. */
    void replaceBySourceId(long sourceId, List<KnowledgeChunk> chunks);

    void deleteBySourceId(long sourceId);

    /**
     * Every chunk of every source the company has finished indexing, agent-wide and
     * company-wide alike. Read once per company and held in memory; the per-agent
     * narrowing happens there, not here.
     */
    List<IndexedChunk> findIndexedByCompanyId(long companyId);
}
