package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeBaseUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;

import java.util.List;
import java.util.Locale;

/**
 * Curated question-and-answer lookup: matches what the caller said against the company's and the
 * agent's own knowledge items by key and keywords. There are no built-in answers, so a company
 * without items gets none.
 */
@Service
public class KnowledgeBaseLookup {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseLookup.class);

    private final KnowledgeBaseUseCase knowledgeBaseUseCase;

    public KnowledgeBaseLookup(KnowledgeBaseUseCase knowledgeBaseUseCase) {
        this.knowledgeBaseUseCase = knowledgeBaseUseCase;
    }

    /**
     * Finds the most relevant knowledge snippet for the given caller question or objection,
     * narrowed to what this agent may answer from: its own items plus the company-wide ones.
     */
    public String findRelevantKnowledge(long companyId, Long agentId, String query, String language) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        try {
            List<KnowledgeItem> dbItems = knowledgeBaseUseCase.findAllActiveByCompanyIdAndAgentId(companyId, agentId);
            for (var item : dbItems) {
                if (matchesDbItem(lower, item)) {
                    log.debug("Knowledge base matched company DB item '{}'", item.key());
                    return item.answerForLanguage(language);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query knowledge base repository: {}", e.getMessage());
        }
        return null;
    }

    private static boolean matchesDbItem(String query, KnowledgeItem item) {
        if (query.contains(item.key().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (item.keywords() != null && !item.keywords().isBlank()) {
            String[] split = item.keywords().split("[,;\s]+");
            for (String kw : split) {
                if (!kw.isBlank() && query.contains(kw.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}
