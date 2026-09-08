package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Knowledge Base (RAG) REST API.
 */
@RequestMapping("/api/knowledge-base")
public interface KnowledgeBaseController {

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<KnowledgeItemResponse>> create(@CurrentCompanyId long companyId,
                                                                  @Valid @RequestBody KnowledgeItemCreateRequest request);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<KnowledgeItemResponse>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<KnowledgeItemResponse>> update(@CurrentCompanyId long companyId, @PathVariable long id,
                                                               @Valid @RequestBody KnowledgeItemUpdateRequest request);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @PostMapping({"/list", "/filter"})
    ResponseEntity<ResponseData<PageableData<KnowledgeItemResponse>>> list(@CurrentCompanyId long companyId,
            @Valid @RequestBody KnowledgeItemFilter filter);
}
