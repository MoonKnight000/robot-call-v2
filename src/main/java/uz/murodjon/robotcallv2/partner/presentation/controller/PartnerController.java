package uz.murodjon.robotcallv2.partner.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.partner.application.dto.BrandingResponse;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientRequest;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientResponse;
import uz.murodjon.robotcallv2.partner.application.dto.PartnerClientRow;
import uz.murodjon.robotcallv2.partner.application.dto.UpdatePartnerBrandingRequest;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClientFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The partner (reseller / white-label) program: the platform enrols a partner, the partner
 * brands its console and signs up its clients, and every panel asks which brand to show.
 */
@RequestMapping("/api")
public interface PartnerController {

    /** Platform-only: makes the company a partner (or changes its brand). */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/partners/{companyId}/branding")
    ResponseEntity<ResponseData<PartnerBranding>> upsertBrandingByPlatform(
            @PathVariable long companyId,
            @Valid @RequestBody UpdatePartnerBrandingRequest request);

    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @GetMapping("/partner/branding")
    ResponseEntity<ResponseData<PartnerBranding>> findOwnBranding(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('COMPANY_EDIT')")
    @PutMapping("/partner/branding")
    ResponseEntity<ResponseData<PartnerBranding>> updateOwnBranding(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdatePartnerBrandingRequest request);

    /** A new client company with its first administrator; the activation token comes back to the partner. */
    @PreAuthorize("hasAuthority('COMPANY_EDIT')")
    @PostMapping("/partner/clients")
    ResponseEntity<ResponseData<CreatePartnerClientResponse>> createClient(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreatePartnerClientRequest request);

    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @PostMapping("/partner/clients/list")
    ResponseEntity<ResponseData<PageableData<PartnerClientRow>>> findClients(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody PartnerClientFilter filter);

    /** The brand the logged-in company's panel shows; {@code data} null means our own. */
    @GetMapping("/branding")
    ResponseEntity<ResponseData<BrandingResponse>> findBranding(@CurrentCompanyId long companyId);

    /** No login: the login page asks for the brand of the host it was opened on. */
    @GetMapping("/public/branding")
    ResponseEntity<ResponseData<BrandingResponse>> findPublicBranding(@RequestParam String domain);
}
