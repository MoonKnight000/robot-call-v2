package uz.murodjon.robotcallv2.crm.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmCatalogUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class UysotCatalogControllerImpl implements UysotCatalogController {

    private final CrmCatalogUseCase crmCatalogUseCase;

    public UysotCatalogControllerImpl(CrmCatalogUseCase crmCatalogUseCase) {
        this.crmCatalogUseCase = crmCatalogUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<CrmPipe>>> pipes(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(crmCatalogUseCase.findPipes(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CrmEmployee>>> employees(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(crmCatalogUseCase.findEmployees(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CrmHouse>>> houses(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(crmCatalogUseCase.findHouses(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CrmFlat>>> searchFlats(long companyId, FlatSearchFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(crmCatalogUseCase.searchFlats(companyId, filter)));
    }
}
