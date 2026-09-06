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
 * Company (tenant) CRUD API (ROADMAP B.1).
 */
@RequestMapping("/api")
public interface CompanyController {

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/companies")
    ResponseEntity<ResponseData<Company>> create(@Valid @RequestBody CreateCompanyRequest r);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/companies/list")
    ResponseEntity<ResponseData<PageableData<Company>>> list(@Valid @RequestBody CompanyFilter filter);

    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @GetMapping("/companies/{id}")
    ResponseEntity<ResponseData<Company>> get(@CurrentCompanyId long callerCompanyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('COMPANY_EDIT')")
    @PutMapping("/companies/{id}")
    ResponseEntity<ResponseData<Company>> update(@CurrentCompanyId long callerCompanyId, @PathVariable long id,
                                                 @Valid @RequestBody UpdateCompanyRequest request);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/companies/{id}/status")
    ResponseEntity<ResponseData<Company>> updateStatus(@PathVariable long id,
                                                       @Valid @RequestBody UpdateCompanyStatusRequest r);

    @PreAuthorize("hasAuthority('COMPANY_EDIT')")
    @PostMapping("/companies/{id}/logo")
    ResponseEntity<ResponseData<Company>> uploadLogo(@CurrentCompanyId long callerCompanyId, @PathVariable long id,
                                                     @RequestParam("file") MultipartFile file);

    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @GetMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> getConfig(@CurrentCompanyId long callerCompanyId,
                                                          @PathVariable long id);

    @PreAuthorize("hasAuthority('COMPANY_EDIT')")
    @PutMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> updateConfig(@CurrentCompanyId long callerCompanyId,
                                                             @PathVariable long id,
                                                             @Valid @RequestBody UpdateCompanyConfigRequest request);
}
