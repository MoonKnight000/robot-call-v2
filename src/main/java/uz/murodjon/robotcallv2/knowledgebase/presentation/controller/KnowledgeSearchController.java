package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeInsightsResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSearchRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSearchResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The knowledge base from outside a call: the playground that asks it a question the way a call would, and the
 * insights that show how calls used it (docs/api/knowledge-base.md §Playground, §Insights).
 */
@RequestMapping("/api/knowledge-base")
public interface KnowledgeSearchController {

    /** Answers the question as a call of {@code agentId} would: the curated answer and the passages, with scores. */
    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @PostMapping("/search")
    ResponseEntity<ResponseData<KnowledgeSearchResponse>> search(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody KnowledgeSearchRequest request);

    /**
     * Hit rate, questions nothing was found for, and the most used sources and answers over the last
     * {@code days} (1–90), narrowed to one agent when given.
     */
    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @GetMapping("/insights")
    ResponseEntity<ResponseData<KnowledgeInsightsResponse>> insights(
            @CurrentCompanyId long companyId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) Long agentId);
}
