package uz.murodjon.robotcallv2.pack.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.pack.application.dto.GeneratePackRequest;
import uz.murodjon.robotcallv2.pack.application.dto.InstallDraftRequest;
import uz.murodjon.robotcallv2.pack.application.dto.InstallPackRequest;
import uz.murodjon.robotcallv2.pack.application.dto.PackInstallResponse;
import uz.murodjon.robotcallv2.pack.application.dto.PackRow;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Industry packs ({@code /api/packs}, UNIVERSAL-OPERATOR-PLAN C.1): a ready agent for one
 * kind of business — its scenario, tools, catalog and test cases — installed in one request.
 */
@RequestMapping("/api/packs")
public interface PackController {

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<PackRow>>> list();

    /**
     * Creates the pack's agent and everything around it in this company. The agent is a
     * draft like any other: its tools fail until the secrets listed in {@code missingSecrets}
     * are set, and it is edited, evaluated and published through {@code /api/ai-agents}.
     * Installing twice gives two independent agents.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{key}/install")
    ResponseEntity<ResponseData<PackInstallResponse>> install(
            @CurrentCompanyId long companyId,
            @PathVariable String key,
            @Valid @RequestBody(required = false) InstallPackRequest request);

    /**
     * Writes a pack for a business the catalog has no pack for (UNIVERSAL-OPERATOR-PLAN E.1):
     * the description in the owner's own words, and optionally the API document its agent
     * should call. Creates nothing — the draft comes back to be read, edited and then sent to
     * {@code /install-draft}.
     *
     * <p>The tools in the draft are built from the document by the same importer
     * {@code /api/tools/import} uses; the model writes only what the caller hears.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/generate")
    ResponseEntity<ResponseData<VerticalPack>> generate(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody GeneratePackRequest request);

    /** Installs a draft — a generated pack as the person edited it — exactly as a catalog pack. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/install-draft")
    ResponseEntity<ResponseData<PackInstallResponse>> installDraft(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody InstallDraftRequest request);
}
