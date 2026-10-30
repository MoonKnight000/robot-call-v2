package uz.murodjon.robotcallv2.company.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.company.application.dto.*;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Company (tenant) CRUD API (ROADMAP B.1, docs/api/companies.md).
 */
@RequestMapping("/api")
public interface CompanyController {

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/companies")
    ResponseEntity<ResponseData<Company>> create(@Valid @RequestBody CreateCompanyRequest request);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/companies/list")
    ResponseEntity<ResponseData<PageableData<Company>>> list(@Valid @RequestBody CompanyFilter filter);

    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @GetMapping("/companies/{id}")
    ResponseEntity<ResponseData<Company>> get(
            @CurrentCompanyId long callerCompanyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('COMPANY_EDIT')")
    @PutMapping("/companies/{id}")
    ResponseEntity<ResponseData<Company>> update(
            @CurrentCompanyId long callerCompanyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateCompanyRequest request);

    /**
     * Platform-only: the whole company in one form — name, address, status (ACTIVE / SUSPENDED = blocked /
     * DELETED), end date, call limit and switched-off features. Every field is replaced.
     */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/companies/{id}/platform")
    ResponseEntity<ResponseData<Company>> updateByPlatform(
            @PathVariable long id,
            @Valid @RequestBody UpdateCompanyByPlatformRequest request);

    @PreAuthorize("hasAuthority('COMPANY_EDIT')")
    @PostMapping("/companies/{id}/logo")
    ResponseEntity<ResponseData<Company>> uploadLogo(
            @CurrentCompanyId long callerCompanyId,
            @PathVariable long id,
            @RequestParam("file") MultipartFile file);

    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @GetMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> getConfig(
            @CurrentCompanyId long callerCompanyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('COMPANY_EDIT')")
    @PutMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> updateConfig(
            @CurrentCompanyId long callerCompanyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateCompanyConfigRequest request);

    /** Platform-only: the telephony engine (ASTERISK / LIVEKIT) the company's calls run on. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/companies/{id}/telephony-engine")
    ResponseEntity<ResponseData<TelephonyEngineResponse>> getTelephonyEngine(@PathVariable long id);

    /** Platform-only: switches the company's telephony engine. The company neither sees nor changes it. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/companies/{id}/telephony-engine")
    ResponseEntity<ResponseData<TelephonyEngineResponse>> updateTelephonyEngine(
            @PathVariable long id,
            @Valid @RequestBody UpdateTelephonyEngineRequest request);
}
