package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.UpdateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Knowledge Base sources — the documents and links an AI agent answers from, as
 * opposed to the hand-written question/answer items of {@link KnowledgeBaseController}.
 */
@RequestMapping("/api/knowledge-base/sources")
public interface KnowledgeSourceController {

    /** Registers one or more documents or links, optionally bound to a single agent. */
    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<List<KnowledgeSourceRow>>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateKnowledgeSourceRequest request);

    /** All sources of the company, or only those bound to {@code agentId} when given. */
    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<KnowledgeSourceRow>>> list(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) Long agentId);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<KnowledgeSourceRow>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<KnowledgeSourceRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateKnowledgeSourceRequest request);

    /** Puts a failed source back in the queue and clears the error it carried. */
    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PostMapping("/{id:\\d+}/retry")
    ResponseEntity<ResponseData<KnowledgeSourceRow>> retry(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
