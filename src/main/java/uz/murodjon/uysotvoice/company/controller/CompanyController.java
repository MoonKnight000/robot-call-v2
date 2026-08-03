package uz.murodjon.uysotvoice.company.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.dto.CompanyFilter;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.company.dto.CreateCompanyRequest;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyConfigRequest;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyRequest;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/** Company (tenant) management API (ROADMAP B.1) — onboarding, identity and settings. */
@RequestMapping("/api")
public interface CompanyController {

    @PostMapping("/companies")
    ResponseEntity<ResponseData<Company>> create(@Valid @RequestBody CreateCompanyRequest r);

    @PostMapping("/companies/list")
    ResponseEntity<ResponseData<PageableData<Company>>> list(@Valid @RequestBody CompanyFilter filter);

    @GetMapping("/companies/{id}")
    ResponseEntity<ResponseData<Company>> get(@PathVariable long id);

    @PutMapping("/companies/{id}")
    ResponseEntity<ResponseData<Company>> update(@PathVariable long id, @Valid @RequestBody UpdateCompanyRequest r);

    /** Settings (ROADMAP B.1/B.3): supported languages, strict dial window, timezone. */
    @GetMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> getConfig(@PathVariable long id);

    @PutMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> updateConfig(
            @PathVariable long id, @Valid @RequestBody UpdateCompanyConfigRequest r);
}
