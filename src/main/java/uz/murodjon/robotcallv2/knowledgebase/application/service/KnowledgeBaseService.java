package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeBaseUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeBaseRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.service.KnowledgeValidator;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeBaseService implements KnowledgeBaseUseCase {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public KnowledgeItemResponse create(long companyId, KnowledgeItemCreateRequest request) {
        KnowledgeValidator.validateKey(request.key());
        KnowledgeValidator.validateAnswer(request.answerUz());
        KnowledgeItem item = new KnowledgeItem(
                0L,
                companyId,
                request.key().trim(),
                request.topic().trim(),
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
        return toResponse(saved);
    }

    @Override
    public KnowledgeItemResponse get(long companyId, long id) {
        KnowledgeItem item = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.KNOWLEDGE_ITEM_NOT_FOUND, id));
        return toResponse(item);
    }

    @Override
    @Transactional
    public KnowledgeItemResponse update(long companyId, long id, KnowledgeItemUpdateRequest request) {
        KnowledgeValidator.validateKey(request.key());
        KnowledgeValidator.validateAnswer(request.answerUz());
        KnowledgeItem existing = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.KNOWLEDGE_ITEM_NOT_FOUND, id));

        KnowledgeItem updated = new KnowledgeItem(
                existing.id(),
                companyId,
                request.key().trim(),
                request.topic().trim(),
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
        List<KnowledgeItemResponse> responses = repository.findAllByCompanyId(companyId, filter).stream()
                .map(this::toResponse)
                .toList();
        return PageableData.of(responses, filter.pageable().getPageNumber(), filter.pageable().getPageSize(),
                repository.countByCompanyId(companyId, filter));
    }

    @Override
    public String findRelevantAnswer(long companyId, String query, String language) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        List<KnowledgeItem> items = repository.findAllActiveByCompanyId(companyId);
        for (KnowledgeItem item : items) {
            if (matches(lower, item)) {
                return item.answerForLanguage(language);
            }
        }
        return null;
    }

    private boolean matches(String query, KnowledgeItem item) {
        if (query.contains(item.key().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (item.keywords() != null && !item.keywords().isBlank()) {
            String[] split = item.keywords().split("[,;\\s]+");
            for (String kw : split) {
                if (!kw.isBlank() && query.contains(kw.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private KnowledgeItemResponse toResponse(KnowledgeItem item) {
        return new KnowledgeItemResponse(
                item.id(),
                item.companyId(),
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
