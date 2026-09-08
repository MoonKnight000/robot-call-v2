package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceItem;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.UpdateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeSourceUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeSourceRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSource;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The documents and links an agent answers from. A source with no {@code agentId} belongs
 * to the whole company and is offered to every one of its agents.
 *
 * <p>This service owns the row; the text behind it is extracted, split and embedded by
 * {@link KnowledgeIndexingService} off a queue. Every path that changes what a source
 * points at ends by putting it back on that queue — creating one, re-pointing its URL,
 * or an operator retrying a failure.
 */
@Service
public class KnowledgeSourceService implements KnowledgeSourceUseCase {

    private final KnowledgeSourceRepository repository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeIndexingService indexingService;
    private final KnowledgeRetrievalUseCase knowledgeRetrievalUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final AuditService auditService;

    public KnowledgeSourceService(KnowledgeSourceRepository repository,
                                  KnowledgeChunkRepository chunkRepository,
                                  KnowledgeIndexingService indexingService,
                                  KnowledgeRetrievalUseCase knowledgeRetrievalUseCase,
                                  AiAgentUseCase aiAgentUseCase,
                                  AuditService auditService) {
        this.repository = repository;
        this.chunkRepository = chunkRepository;
        this.indexingService = indexingService;
        this.knowledgeRetrievalUseCase = knowledgeRetrievalUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public List<KnowledgeSourceRow> createKnowledgeSources(long companyId, CreateKnowledgeSourceRequest request) {
        String agentName = requireAgentName(companyId, request.agentId());

        List<KnowledgeSource> created = new ArrayList<>();
        Instant now = Instant.now();
        for (CreateKnowledgeSourceItem item : request.documents()) {
            validate(item);
            created.add(repository.save(new KnowledgeSource(
                    0L,
                    companyId,
                    request.agentId(),
                    item.name().trim(),
                    item.sourceType(),
                    item.originalFileName(),
                    item.storedFileId(),
                    item.url() != null ? item.url().trim() : null,
                    KnowledgeSourceStatus.PENDING,
                    null,
                    null,
                    null,
                    0,
                    now
            )));
        }

        List<KnowledgeSourceRow> rows = new ArrayList<>(created.size());
        for (KnowledgeSource source : created) {
            audit(companyId, "KNOWLEDGE_SOURCE_CREATE", source);
            indexingService.enqueue(source);
            rows.add(toRow(source, agentName));
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeSourceRow> findKnowledgeSources(long companyId, Long agentId) {
        if (agentId == null) {
            return repository.findByCompanyId(companyId).stream()
                    .map(source -> toRow(source, agentNameOf(companyId, source.agentId())))
                    .toList();
        }
        String agentName = requireAgentName(companyId, agentId);
        return repository.findByCompanyIdAndAgentId(companyId, agentId).stream()
                .map(source -> toRow(source, agentName))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeSourceRow findKnowledgeSource(long companyId, long id) {
        KnowledgeSource source = requireSource(companyId, id);
        return toRow(source, agentNameOf(companyId, source.agentId()));
    }

    @Override
    @Transactional
    public KnowledgeSourceRow updateKnowledgeSource(long companyId, long id, UpdateKnowledgeSourceRequest request) {
        KnowledgeSource existing = requireSource(companyId, id);

        Long agentId = existing.agentId();
        if (request.agentId() != null) {
            aiAgentUseCase.requireAgent(companyId, request.agentId());
            agentId = request.agentId();
        }
        String url = request.url() != null ? request.url().trim() : existing.url();
        // Only a changed address means the text behind the source changed. A rename or a
        // move to another agent leaves the passages exactly as they were, and re-indexing
        // them would spend the embedding quota to produce the same vectors.
        boolean reindex = !Objects.equals(url, existing.url());

        KnowledgeSource updated = new KnowledgeSource(
                existing.id(),
                existing.companyId(),
                agentId,
                request.name() != null ? request.name().trim() : existing.name(),
                existing.sourceType(),
                existing.originalFileName(),
                existing.storedFileId(),
                url,
                existing.status(),
                existing.errorCode(),
                existing.errorMessage(),
                existing.lastIndexedAt(),
                existing.chunkCount(),
                existing.createdAt()
        );
        KnowledgeSource saved = repository.save(reindex ? updated.queuedForIndexing() : updated);
        audit(companyId, "KNOWLEDGE_SOURCE_UPDATE", saved);
        if (reindex) {
            indexingService.enqueue(saved);
        } else {
            // The passages did not change, but which agent may see them did.
            knowledgeRetrievalUseCase.evictCompany(companyId);
        }
        return toRow(saved, agentNameOf(companyId, saved.agentId()));
    }

    @Override
    @Transactional
    public KnowledgeSourceRow retryKnowledgeSource(long companyId, long id) {
        KnowledgeSource existing = requireSource(companyId, id);
        KnowledgeSource saved = repository.save(existing.queuedForIndexing());
        audit(companyId, "KNOWLEDGE_SOURCE_RETRY", saved);
        indexingService.enqueue(saved);
        return toRow(saved, agentNameOf(companyId, saved.agentId()));
    }

    @Override
    @Transactional
    public void deleteKnowledgeSource(long companyId, long id) {
        KnowledgeSource existing = requireSource(companyId, id);
        chunkRepository.deleteBySourceId(id);
        repository.deleteByCompanyIdAndId(companyId, id);
        knowledgeRetrievalUseCase.evictCompany(companyId);
        audit(companyId, "KNOWLEDGE_SOURCE_DELETE", existing);
    }

    /**
     * A URL source needs an address and a file source needs a file. Caught here rather
     * than by the indexer so an operator learns it while the form is still open, not from
     * a {@code FAILED} row minutes later.
     */
    private static void validate(CreateKnowledgeSourceItem item) {
        if (item.sourceType() == KnowledgeSourceType.URL) {
            if (item.url() == null || item.url().isBlank()) {
                throw new ValidationException(ErrorCode.KNOWLEDGE_SOURCE_URL_REQUIRED);
            }
        } else if (item.storedFileId() == null) {
            throw new ValidationException(ErrorCode.KNOWLEDGE_SOURCE_FILE_REQUIRED, item.sourceType());
        }
    }

    private KnowledgeSource requireSource(long companyId, long id) {
        return repository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.KNOWLEDGE_SOURCE_NOT_FOUND, id));
    }

    /** Refuses an agent that is not this company's, so a source cannot be bound across tenants. */
    private String requireAgentName(long companyId, Long agentId) {
        return agentId == null ? null : aiAgentUseCase.requireAgent(companyId, agentId).name();
    }

    /**
     * The agent's name for display, or {@code null} when the binding no longer resolves —
     * a listing must not fail because one row points at an agent that has since gone.
     */
    private String agentNameOf(long companyId, Long agentId) {
        if (agentId == null) {
            return null;
        }
        AiAgent agent = aiAgentUseCase.findAgent(companyId, agentId);
        return agent != null ? agent.name() : null;
    }

    private void audit(long companyId, String action, KnowledgeSource source) {
        auditService.record(companyId, action, "knowledge_source", String.valueOf(source.id()), source.name());
    }

    private KnowledgeSourceRow toRow(KnowledgeSource source, String agentName) {
        return new KnowledgeSourceRow(
                source.id(),
                source.companyId(),
                source.agentId(),
                agentName,
                source.name(),
                source.sourceType(),
                source.originalFileName(),
                source.storedFileId(),
                source.url(),
                source.status(),
                source.errorCode(),
                source.errorMessage(),
                source.lastIndexedAt(),
                source.chunkCount(),
                source.createdAt()
        );
    }
}
