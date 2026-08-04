package uz.murodjon.uysotvoice.company.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.dto.CompanyFilter;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.company.dto.CreateCompanyRequest;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyConfigRequest;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyRequest;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyStatusRequest;
import uz.murodjon.uysotvoice.company.service.CompanyConfigService;
import uz.murodjon.uysotvoice.company.service.CompanyService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class CompanyControllerImpl implements CompanyController {

    private final CompanyService service;
    private final CompanyConfigService configService;

    public CompanyControllerImpl(CompanyService service, CompanyConfigService configService) {
        this.service = service;
        this.configService = configService;
    }

    @Override
    public ResponseEntity<ResponseData<Company>> create(CreateCompanyRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<Company>>> list(CompanyFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<Company>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.requireCompany(id)));
    }

    @Override
    public ResponseEntity<ResponseData<Company>> update(long id, UpdateCompanyRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<Company>> updateStatus(long id, UpdateCompanyStatusRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.updateStatus(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<Company>> uploadLogo(long id, MultipartFile file) {
        return ResponseEntity.ok(ResponseData.ok(service.uploadLogo(id, file)));
    }

    @Override
    public ResponseEntity<ResponseData<CompanyConfig>> getConfig(long id) {
        return ResponseEntity.ok(ResponseData.ok(configService.requireConfigForApi(id)));
    }

    @Override
    public ResponseEntity<ResponseData<CompanyConfig>> updateConfig(long id, UpdateCompanyConfigRequest r) {
        return ResponseEntity.ok(ResponseData.ok(configService.update(id, r)));
    }
}
