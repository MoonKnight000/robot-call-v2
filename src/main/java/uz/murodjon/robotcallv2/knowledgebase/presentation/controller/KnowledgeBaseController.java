package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemResponse;
import uz.murodjon.robotcallv2.knowledgebase.presentation.dto.KnowledgeItemUpdateRequest;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Knowledge Base (RAG) REST API.
 */
@RequestMapping("/api/knowledge-base")
public interface KnowledgeBaseController {

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<KnowledgeItemResponse>> create(@Valid @RequestBody KnowledgeItemCreateRequest request);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<KnowledgeItemResponse>> get(@PathVariable long id);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<KnowledgeItemResponse>> update(@PathVariable long id,
                                                               @Valid @RequestBody KnowledgeItemUpdateRequest request);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@PathVariable long id);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @PostMapping({"/list", "/filter"})
    ResponseEntity<ResponseData<PageableData<KnowledgeItemResponse>>> list(@Valid @RequestBody KnowledgeItemFilter filter);
}
