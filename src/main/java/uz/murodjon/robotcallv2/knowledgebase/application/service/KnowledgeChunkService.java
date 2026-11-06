package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeChunkRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.UpdateKnowledgeChunkRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeChunkUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeSourceRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSourcePage;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.Tokens;

import java.util.List;

/**
 * A person's hand on a source's passages. A passage that is wrong is switched off or corrected here instead of
 * editing the document and indexing it again; both survive a refresh of the source as long as the page still
 * reads the same where the passage came from.
 */
@Service
public class KnowledgeChunkService implements KnowledgeChunkUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeChunkService.class);
    private static final int MAX_PAGE_SIZE = 200;

    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeSourceRepository sourceRepository;
    private final TextEmbedderPort textEmbedder;
    private final AuditUseCase auditService;

    public KnowledgeChunkService(KnowledgeChunkRepository chunkRepository, KnowledgeSourceRepository sourceRepository,
                                 TextEmbedderPort textEmbedder, AuditUseCase auditService) {
        this.chunkRepository = chunkRepository;
        this.sourceRepository = sourceRepository;
        this.textEmbedder = textEmbedder;
        this.auditService = auditService;
    }

    @Override
    public PageableData<KnowledgeChunkRow> findChunks(long companyId, long sourceId, String search, String pageUrl,
                                                      int page, int size) {
        requireSource(companyId, sourceId);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageableData<KnowledgeChunk> chunks = chunkRepository.findPageBySourceId(sourceId, search, pageUrl,
                Math.max(page, 0), safeSize);
        return new PageableData<>(chunks.totalPages(), chunks.currentPage(), chunks.totalElements(),
                chunks.data().stream().map(KnowledgeChunkService::toRow).toList());
    }

    @Override
    public List<KnowledgeSourcePage> findSourcePages(long companyId, long sourceId) {
        requireSource(companyId, sourceId);
        return chunkRepository.findPagesBySourceId(sourceId);
    }

    @Override
    @Transactional
    public KnowledgeChunkRow updateChunk(long companyId, long sourceId, long chunkId, UpdateKnowledgeChunkRequest request) {
        requireSource(companyId, sourceId);
        KnowledgeChunk chunk = chunkRepository.findBySourceIdAndId(sourceId, chunkId);
        if (chunk == null) {
            throw new NotFoundException(ErrorCode.KNOWLEDGE_CHUNK_NOT_FOUND, chunkId, sourceId);
        }
        KnowledgeChunk updated = chunk;
        if (request.content() != null) {
            String content = request.content().trim();
            if (content.isEmpty()) {
                throw new ValidationException(ErrorCode.KNOWLEDGE_CHUNK_CONTENT_BLANK);
            }
            if (!content.equals(chunk.content())) {
                updated = updated.edited(content, Tokens.hash(content), embed(companyId, content));
            }
        }
        if (request.disabled() != null) {
            updated = updated.withDisabled(request.disabled());
        }
        if (updated != chunk) {
            chunkRepository.updateCuration(updated);
            auditService.record(companyId, "KNOWLEDGE_CHUNK_UPDATE", "knowledge_chunk", String.valueOf(chunkId),
                    "source " + sourceId + (updated.disabled() ? ", switched off" : ""));
        }
        return toRow(updated);
    }

    /** The edited text's vector; null leaves it to the backfill rather than failing the edit. */
    private float[] embed(long companyId, String content) {
        try {
            List<float[]> vectors = textEmbedder.embedAll(List.of(content));
            return vectors.isEmpty() ? null : vectors.getFirst();
        } catch (Exception e) {
            log.warn("[company {}] Could not embed an edited knowledge passage: {}", companyId, e.getMessage());
            return null;
        }
    }

    private void requireSource(long companyId, long sourceId) {
        sourceRepository.findByCompanyIdAndId(companyId, sourceId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.KNOWLEDGE_SOURCE_NOT_FOUND, sourceId));
    }

    private static KnowledgeChunkRow toRow(KnowledgeChunk chunk) {
        return new KnowledgeChunkRow(chunk.id(), chunk.sourceId(), chunk.ordinal(), chunk.content(), chunk.pageUrl(),
                chunk.disabled(), chunk.originalHash() != null);
    }
}
