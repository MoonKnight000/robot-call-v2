package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeChunkRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.UpdateKnowledgeChunkRequest;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSourcePage;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

/** The passages inside one source: read them, correct them, switch them off. */
public interface KnowledgeChunkUseCase {

    /**
     * One page of the source's passages in document order.
     *
     * @param search  only passages containing this text (case-blind); blank for all
     * @param pageUrl only passages from this page of a sitemap; null for all
     */
    PageableData<KnowledgeChunkRow> findChunks(long companyId, long sourceId, String search, String pageUrl,
                                               int page, int size);

    /** The pages a sitemap or URL source read, with how many passages each gave. */
    List<KnowledgeSourcePage> findSourcePages(long companyId, long sourceId);

    KnowledgeChunkRow updateChunk(long companyId, long sourceId, long chunkId, UpdateKnowledgeChunkRequest request);
}
