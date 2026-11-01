package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeCollectionRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.SaveKnowledgeCollectionRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Named knowledge bases: a set of sources attached to any number of agents. Sources are added to
 * one through {@link KnowledgeSourceController} with a {@code collectionId}.
 */
@RequestMapping("/api/knowledge-base/collections")
public interface KnowledgeCollectionController {

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<KnowledgeCollectionRow>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody SaveKnowledgeCollectionRequest request);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<KnowledgeCollectionRow>>> list(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_READ')")
    @GetMapping("/{id:\\d+}/sources")
    ResponseEntity<ResponseData<List<KnowledgeSourceRow>>> listSources(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /** Renames the collection; {@code agentIds}, when given, replaces the agents attached to it. */
    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<KnowledgeCollectionRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody SaveKnowledgeCollectionRequest request);

    /** Deletes the collection together with its sources. */
    @PreAuthorize("hasAuthority('KNOWLEDGE_BASE_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
