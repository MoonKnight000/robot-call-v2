package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeCollectionRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.SaveKnowledgeCollectionRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeCollectionUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeSourceUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeCollectionRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeSourceRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeCollection;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSource;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;

import java.time.Instant;
import java.util.List;

/**
 * Named knowledge bases: sources grouped under a name and attached to any number of agents.
 * An agent answers from its own sources, the company-wide ones and those of the collections
 * attached to it.
 */
@Service
public class KnowledgeCollectionService implements KnowledgeCollectionUseCase {

    private final KnowledgeCollectionRepository repository;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeSourceUseCase knowledgeSourceUseCase;
    private final KnowledgeRetrievalUseCase knowledgeRetrievalUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final AuditUseCase auditService;
    private final FileStorageUseCase fileStorageUseCase;

    public KnowledgeCollectionService(KnowledgeCollectionRepository repository,
                                      KnowledgeSourceRepository sourceRepository,
                                      KnowledgeSourceUseCase knowledgeSourceUseCase,
                                      KnowledgeRetrievalUseCase knowledgeRetrievalUseCase,
                                      AiAgentUseCase aiAgentUseCase,
                                      AuditUseCase auditService,
                                      FileStorageUseCase fileStorageUseCase) {
        this.repository = repository;
        this.sourceRepository = sourceRepository;
        this.knowledgeSourceUseCase = knowledgeSourceUseCase;
        this.knowledgeRetrievalUseCase = knowledgeRetrievalUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.auditService = auditService;
        this.fileStorageUseCase = fileStorageUseCase;
    }

    @Override
    @Transactional
    public KnowledgeCollectionRow createKnowledgeCollection(long companyId, SaveKnowledgeCollectionRequest request) {
        String name = request.name().trim();
        requireNameFree(companyId, name, 0L);
        List<Long> agentIds = request.agentIds() != null ? request.agentIds() : List.of();
        requireAgents(companyId, agentIds);
        KnowledgeCollection saved = repository.save(new KnowledgeCollection(0L, companyId, name, Instant.now()));
        repository.replaceAgents(saved.id(), agentIds);
        knowledgeRetrievalUseCase.evictCompany(companyId);
        audit(companyId, "KNOWLEDGE_COLLECTION_CREATE", saved);
        return toRow(saved, 0, agentIds.stream().distinct().toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeCollectionRow> findKnowledgeCollections(long companyId) {
        var counts = repository.countSourcesByCompanyId(companyId);
        var agents = repository.findAgentIdsByCompanyId(companyId);
        return repository.findByCompanyId(companyId).stream()
                .map(collection -> toRow(collection, counts.getOrDefault(collection.id(), 0),
                        agents.getOrDefault(collection.id(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeSourceRow> findKnowledgeCollectionSources(long companyId, long id) {
        return knowledgeSourceUseCase.findKnowledgeSourcesByCollectionId(companyId, id);
    }

    @Override
    @Transactional
    public KnowledgeCollectionRow updateKnowledgeCollection(long companyId, long id, SaveKnowledgeCollectionRequest request) {
        KnowledgeCollection existing = requireCollection(companyId, id);
        String name = request.name().trim();
        requireNameFree(companyId, name, id);
        KnowledgeCollection saved = repository.save(new KnowledgeCollection(id, companyId, name, existing.createdAt()));
        if (request.agentIds() != null) {
            requireAgents(companyId, request.agentIds());
            repository.replaceAgents(id, request.agentIds());
            // Who may read the collection's passages changed, though the passages did not.
            knowledgeRetrievalUseCase.evictCompany(companyId);
        }
        audit(companyId, "KNOWLEDGE_COLLECTION_UPDATE", saved);
        return findKnowledgeCollections(companyId).stream().filter(row -> row.id() == id).findFirst().orElseThrow();
    }

    @Override
    @Transactional
    public void deleteKnowledgeCollection(long companyId, long id) {
        KnowledgeCollection existing = requireCollection(companyId, id);
        // The rows go with the collection (their chunks with them); the uploaded files do not.
        for (KnowledgeSource source : sourceRepository.findByCompanyIdAndCollectionId(companyId, id)) {
            if (source.storedFileId() != null) {
                fileStorageUseCase.delete(companyId, source.storedFileId());
            }
        }
        repository.deleteByCompanyIdAndId(companyId, id);
        knowledgeRetrievalUseCase.evictCompany(companyId);
        audit(companyId, "KNOWLEDGE_COLLECTION_DELETE", existing);
    }

    private KnowledgeCollection requireCollection(long companyId, long id) {
        return repository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.KNOWLEDGE_COLLECTION_NOT_FOUND, id));
    }

    private void requireNameFree(long companyId, String name, long exceptId) {
        repository.findByCompanyIdAndName(companyId, name)
                .filter(other -> other.id() != exceptId)
                .ifPresent(other -> {
                    throw new ConflictException(ErrorCode.KNOWLEDGE_COLLECTION_NAME_TAKEN, name);
                });
    }

    private void requireAgents(long companyId, List<Long> agentIds) {
        for (Long agentId : agentIds) {
            aiAgentUseCase.requireAgent(companyId, agentId);
        }
    }

    private void audit(long companyId, String action, KnowledgeCollection collection) {
        auditService.record(companyId, action, "knowledge_collection", String.valueOf(collection.id()), collection.name());
    }

    private static KnowledgeCollectionRow toRow(KnowledgeCollection collection, int sourceCount, List<Long> agentIds) {
        return new KnowledgeCollectionRow(collection.id(), collection.name(), sourceCount, List.copyOf(agentIds),
                collection.createdAt());
    }
}
