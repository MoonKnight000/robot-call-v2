package uz.murodjon.robotcallv2.company.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.company.application.dto.*;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Company (tenant) CRUD API (ROADMAP B.1).
 */
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

    @PutMapping("/companies/{id}/status")
    ResponseEntity<ResponseData<Company>> updateStatus(@PathVariable long id,
                                                       @Valid @RequestBody UpdateCompanyStatusRequest r);

    @PostMapping("/companies/{id}/logo")
    ResponseEntity<ResponseData<Company>> uploadLogo(@PathVariable long id,
                                                     @RequestParam("file") MultipartFile file);

    @GetMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> getConfig(@PathVariable long id);

    @PutMapping("/companies/{id}/config")
    ResponseEntity<ResponseData<CompanyConfig>> updateConfig(@PathVariable long id,
                                                             @Valid @RequestBody UpdateCompanyConfigRequest r);
}
