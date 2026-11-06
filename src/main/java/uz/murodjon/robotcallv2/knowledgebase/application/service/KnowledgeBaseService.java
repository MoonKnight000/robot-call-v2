package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeBaseUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeBaseRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.knowledgebase.domain.service.KnowledgeValidator;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeBaseService implements KnowledgeBaseUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository repository;
    private final AiAgentUseCase aiAgentUseCase;
    private final TextEmbedderPort textEmbedder;

    public KnowledgeBaseService(KnowledgeBaseRepository repository, AiAgentUseCase aiAgentUseCase,
                                TextEmbedderPort textEmbedder) {
        this.repository = repository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.textEmbedder = textEmbedder;
    }

    @Override
    @Transactional
    public KnowledgeItemResponse create(long companyId, KnowledgeItemCreateRequest request) {
        KnowledgeValidator.validateKey(request.key());
        KnowledgeValidator.validateAnswer(request.answerUz());
        requireOwnAgent(companyId, request.agentId());
        KnowledgeItem item = new KnowledgeItem(
                0L,
                companyId,
                request.agentId(),
                request.key().trim(),
                normalizeTopic(request.topic()),
                request.title().trim(),
                request.answerUz().trim(),
                request.answerRu() != null ? request.answerRu().trim() : null,
                request.answerEn() != null ? request.answerEn().trim() : null,
                request.keywords() != null ? request.keywords().trim().toLowerCase(Locale.ROOT) : "",
                true,
                Instant.now(),
                Instant.now()
        );

        KnowledgeItem saved = repository.save(item);
        embed(saved);
        return toResponse(saved);
    }

    @Override
    public KnowledgeItemResponse findItem(long companyId, long id) {
        KnowledgeItem item = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.KNOWLEDGE_ITEM_NOT_FOUND, id));
        return toResponse(item);
    }

    @Override
    @Transactional
    public KnowledgeItemResponse update(long companyId, long id, KnowledgeItemUpdateRequest request) {
        KnowledgeValidator.validateKey(request.key());
        KnowledgeValidator.validateAnswer(request.answerUz());
        requireOwnAgent(companyId, request.agentId());
        KnowledgeItem existing = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.KNOWLEDGE_ITEM_NOT_FOUND, id));

        KnowledgeItem updated = new KnowledgeItem(
                existing.id(),
                companyId,
                request.agentId() != null ? request.agentId() : existing.agentId(),
                request.key().trim(),
                normalizeTopic(request.topic()),
                request.title().trim(),
                request.answerUz().trim(),
                request.answerRu() != null ? request.answerRu().trim() : null,
                request.answerEn() != null ? request.answerEn().trim() : null,
                request.keywords() != null ? request.keywords().trim().toLowerCase(Locale.ROOT) : "",
                request.active(),
                existing.createdAt(),
                Instant.now()
        );

        KnowledgeItem saved = repository.save(updated);
        embed(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.KNOWLEDGE_ITEM_NOT_FOUND, id));
        repository.deleteByIdAndCompanyId(id, companyId);
    }

    @Override
    public PageableData<KnowledgeItemResponse> list(long companyId, KnowledgeItemFilter filter) {
        PageableData<KnowledgeItem> page = repository.findPageByCompanyId(companyId, filter);
        List<KnowledgeItemResponse> responses = page.data().stream()
                .map(this::toResponse)
                .toList();
        return new PageableData<>(page.totalPages(), page.currentPage(), page.totalElements(), responses);
    }

    @Override
    public List<String> findTopics(long companyId) {
        return repository.findTopicsByCompanyId(companyId);
    }

    /**
     * Gives the saved item the vector it is matched by meaning with. Its text changed, so any old vector is
     * wrong: when the model cannot be reached the item is left with none (keyword matching only) and the
     * backfill fills it in — a save never fails on the embedding model.
     */
    private void embed(KnowledgeItem item) {
        float[] vector = null;
        try {
            List<float[]> vectors = textEmbedder.embedAll(List.of(item.embeddingText()));
            vector = vectors.isEmpty() ? null : vectors.getFirst();
        } catch (Exception e) {
            log.warn("[company {}] Could not embed knowledge item {}: {}", item.companyId(), item.id(), e.getMessage());
        }
        repository.updateEmbedding(item.id(), vector);
    }

    /** Refuses an agent that is not this company's, so an item cannot be bound across tenants. */
    private void requireOwnAgent(long companyId, Long agentId) {
        if (agentId != null) {
            aiAgentUseCase.requireAgent(companyId, agentId);
        }
    }

    /**
     * A topic is free text a company names its own categories with — a closed list would
     * refuse every topic a company made up. One spelling per topic, though: "Payment" and
     * "payment" must not become two categories in the filter.
     */
    private static String normalizeTopic(String topic) {
        return topic.trim().toLowerCase(Locale.ROOT);
    }

    private KnowledgeItemResponse toResponse(KnowledgeItem item) {
        return new KnowledgeItemResponse(
                item.id(),
                item.companyId(),
                item.agentId(),
                item.key(),
                item.topic(),
                item.title(),
                item.answerUz(),
                item.answerRu(),
                item.answerEn(),
                item.keywords(),
                item.active(),
                item.createdAt(),
                item.updatedAt()
        );
    }
}
