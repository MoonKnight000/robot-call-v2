package uz.murodjon.robotcallv2.company.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.company.application.dto.*;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class CompanyControllerImpl implements CompanyController {

    private final CompanyUseCase companyUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;

    public CompanyControllerImpl(CompanyUseCase companyUseCase, CompanyConfigUseCase companyConfigUseCase) {
        this.companyUseCase = companyUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<Company>> create(CreateCompanyRequest r) {
        return ResponseEntity.ok(ResponseData.ok(companyUseCase.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<Company>>> list(CompanyFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(companyUseCase.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<Company>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(companyUseCase.requireCompany(id)));
    }

    @Override
    public ResponseEntity<ResponseData<Company>> update(long id, UpdateCompanyRequest r) {
        return ResponseEntity.ok(ResponseData.ok(companyUseCase.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<Company>> updateStatus(long id, UpdateCompanyStatusRequest r) {
        return ResponseEntity.ok(ResponseData.ok(companyUseCase.updateStatus(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<Company>> uploadLogo(long id, MultipartFile file) {
        return ResponseEntity.ok(ResponseData.ok(companyUseCase.uploadLogo(id, file)));
    }

    @Override
    public ResponseEntity<ResponseData<CompanyConfig>> getConfig(long id) {
        return ResponseEntity.ok(ResponseData.ok(companyConfigUseCase.requireConfigForApi(id)));
    }

    @Override
    public ResponseEntity<ResponseData<CompanyConfig>> updateConfig(long id, UpdateCompanyConfigRequest r) {
        return ResponseEntity.ok(ResponseData.ok(companyConfigUseCase.update(id, r)));
    }
}
