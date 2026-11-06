package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeInsightsResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSearchRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSearchResponse;

/** The knowledge base seen from outside a call: try a question on it, and see how calls used it. */
public interface KnowledgeSearchUseCase {

    /** Answers the question exactly as a call of that agent would — the curated answer and the passages. */
    KnowledgeSearchResponse searchKnowledge(long companyId, KnowledgeSearchRequest request);

    /**
     * What calls and chats asked the knowledge base over the last {@code days}, narrowed to one agent when given:
     * how often it answered, what it had nothing for, and what it used most.
     */
    KnowledgeInsightsResponse findInsights(long companyId, int days, Long agentId);
}
