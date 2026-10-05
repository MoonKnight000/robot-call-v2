package uz.murodjon.robotcallv2.quality.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.quality.application.dto.ReviewCallRequest;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.CallReviewFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The review inbox ({@code /api/call-reviews}, UNIVERSAL-OPERATOR-PLAN D.2): the AI calls the
 * quality judge flagged, and a supervisor's verdict on them. The recording and the transcript
 * come from {@code GET /api/reports/calls/{callId}}; this is only the quality side of the call.
 */
@RequestMapping("/api/call-reviews")
public interface CallReviewController {

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_READ')")
    @PostMapping({"/filter", "/list"})
    ResponseEntity<ResponseData<PageableData<CallQuality>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CallReviewFilter filter);

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_READ')")
    @GetMapping("/{callId:\\d+}")
    ResponseEntity<ResponseData<CallQuality>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long callId);

    /** "The agent was right" or "the agent was wrong: <reason>". Changing an earlier verdict is allowed. */
    @PreAuthorize("hasAuthority('QUALITY_REVIEW_EDIT')")
    @PutMapping("/{callId:\\d+}")
    ResponseEntity<ResponseData<CallQuality>> review(
            @CurrentCompanyId long companyId,
            @PathVariable long callId,
            @Valid @RequestBody ReviewCallRequest request);

    /**
     * Writes a knowledge base answer for the call's agent — the request's {@code agentId} is
     * ignored. Needs KNOWLEDGE_BASE_EDIT as well: the answer is what the agent tells callers.
     */
    @PreAuthorize("hasAuthority('QUALITY_REVIEW_EDIT') and hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PostMapping("/{callId:\\d+}/knowledge-item")
    ResponseEntity<ResponseData<CallQuality>> createKnowledgeItem(
            @CurrentCompanyId long companyId,
            @PathVariable long callId,
            @Valid @RequestBody KnowledgeItemCreateRequest request);

    /** Adds a test case to the call's agent, so its eval replays what went wrong here. */
    @PreAuthorize("hasAuthority('QUALITY_REVIEW_EDIT')")
    @PostMapping("/{callId:\\d+}/test-case")
    ResponseEntity<ResponseData<CallQuality>> createTestCase(
            @CurrentCompanyId long companyId,
            @PathVariable long callId,
            @Valid @RequestBody SaveAgentTestCaseRequest request);
}
