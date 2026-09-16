package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.CatalogSearchUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.CatalogItemRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogMatch;
import uz.murodjon.robotcallv2.knowledgebase.domain.service.CatalogRanker;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the catalog rows a caller is asking about (UNIVERSAL-OPERATOR-PLAN B.4).
 *
 * <p>Two candidate lists from the database, one ranking here. The words find the row that
 * carries the caller's colour and size; the embedding finds the one the caller described
 * in other words than the catalog's. Each list is capped at {@link #CANDIDATES} — the
 * vector index and the text index do the narrowing, so a catalog of any size costs the
 * same two indexed queries and a ranking of at most a hundred rows.
 */
@Service
public class CatalogSearchService implements CatalogSearchUseCase {

    private static final Logger log = LoggerFactory.getLogger(CatalogSearchService.class);

    /** How many rows each candidate list may bring: far more than are read to the model, few enough to rank at once. */
    private static final int CANDIDATES = 50;

    private final CatalogItemRepository repository;
    private final TextEmbedderPort textEmbedder;

    public CatalogSearchService(CatalogItemRepository repository, TextEmbedderPort textEmbedder) {
        this.repository = repository;
        this.textEmbedder = textEmbedder;
    }

    @Override
    public List<CatalogMatch> findCatalogMatches(long companyId, Long agentId, String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        List<String> terms = CatalogRanker.terms(query);
        List<CatalogMatch> candidates = new ArrayList<>();
        try {
            if (!terms.isEmpty()) {
                candidates.addAll(repository.findByTerms(companyId, agentId, terms, CANDIDATES));
            }
            float[] vector = textEmbedder.embedQuery(query);
            if (vector != null) {
                candidates.addAll(repository.findNearest(companyId, agentId, vector, CANDIDATES));
            }
        } catch (Exception e) {
            // The caller hears "not found" rather than a failed turn; the log says why.
            log.warn("[company {}] catalog search failed: {}", companyId, e.getMessage());
            return List.of();
        }
        return CatalogRanker.rank(candidates, terms, limit);
    }

    @Override
    public boolean existsByCompanyIdAndAgentId(long companyId, Long agentId) {
        try {
            return repository.existsByCompanyIdAndAgentId(companyId, agentId);
        } catch (Exception e) {
            log.warn("[company {}] catalog lookup failed: {}", companyId, e.getMessage());
            return false;
        }
    }
}
