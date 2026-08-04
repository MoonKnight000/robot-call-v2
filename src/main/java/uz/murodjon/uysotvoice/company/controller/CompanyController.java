package uz.murodjon.uysotvoice.company.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.dto.CompanyFilter;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.company.dto.CreateCompanyRequest;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyConfigRequest;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyRequest;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyStatusRequest;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * Company (tenant) management API (ROADMAP B.1) — onboarding, identity and settings.
 *
 * <p>Role split (report #3, {@code SecurityConfig}): {@code create}/{@code list}/{@code
 * updateStatus} are platform-staff-only ({@code SUPERADMIN}) — a tenant's own {@code
 * ADMIN} cannot create other tenants, browse them, or change its own company's status.
 * {@code get}/{@code update}/{@code getConfig}/{@code updateConfig}/{@code uploadLogo}
 * are self-scoped to the caller's own company in {@code CompanyService} (both roles may
 * call {@code get}; only {@code ADMIN} the rest).
 */
@RequestMapping("/api")
public interface CompanyController {

    /** SUPERADMIN-only. */
    @PostMapping("/companies")
    ResponseEntity<ResponseData<Company>> create(@Valid @RequestBody CreateCompanyRequest r);

    /** SUPERADMIN-only. */
    @PostMapping("/companies/list")
    ResponseEntity<ResponseData<PageableData<Company>>> list(@Valid @RequestBody CompanyFilter filter);

    @GetMapping("/companies/{id}")
    ResponseEntity<ResponseData<Company>> get(@PathVariable long id);

    /** A tenant's own {@code ADMIN} — identity only (name/logo/address), never {@code status}. */
    @PutMapping("/companies/{id}")
    ResponseEntity<ResponseData<Company>> update(@PathVariable long id, @Valid @RequestBody UpdateCompanyRequest r);

    /** SUPERADMIN-only — the only way a company's active/suspended status changes (report #3). */
    @PutMapping("/companies/{id}/status")
    ResponseEntity<ResponseData<Company>> updateStatus(@PathVariable long id, @Valid @RequestBody UpdateCompanyStatusRequest r);

    /**
     * Company logo upload (report #4) — the only way {@code logoFileId} changes; image-
     * only, 5 MB max, served back via {@code GET /api/files/{id}}.
     */
    @PostMapping(value = "/companies/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResponseData<Company>> uploadLogo(@PathVariable long id, @RequestParam("file") MultipartFile file);

    /** Settings (ROADMAP B.1/B.3): supported languages, strict dial window, timezone. */
    @GetMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> getConfig(@PathVariable long id);

    @PutMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> updateConfig(
            @PathVariable long id, @Valid @RequestBody UpdateCompanyConfigRequest r);
}
