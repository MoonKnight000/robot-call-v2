package uz.murodjon.robotcallv2.partner.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.partner.application.dto.BrandingResponse;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientRequest;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientResponse;
import uz.murodjon.robotcallv2.partner.application.dto.PartnerClientRow;
import uz.murodjon.robotcallv2.partner.application.dto.UpdatePartnerBrandingRequest;
import uz.murodjon.robotcallv2.partner.application.port.input.PartnerUseCase;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClientFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class PartnerControllerImpl implements PartnerController {

    private final PartnerUseCase partnerUseCase;

    public PartnerControllerImpl(PartnerUseCase partnerUseCase) {
        this.partnerUseCase = partnerUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PartnerBranding>> upsertBrandingByPlatform(long companyId,
                                                                                  UpdatePartnerBrandingRequest request) {
        return ResponseEntity.ok(ResponseData.ok(partnerUseCase.upsertBrandingByPlatform(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PartnerBranding>> findOwnBranding(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(partnerUseCase.findOwnBranding(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<PartnerBranding>> updateOwnBranding(long companyId,
                                                                           UpdatePartnerBrandingRequest request) {
        return ResponseEntity.ok(ResponseData.ok(partnerUseCase.updateOwnBranding(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<CreatePartnerClientResponse>> createClient(long companyId,
                                                                                  CreatePartnerClientRequest request) {
        return ResponseEntity.ok(ResponseData.ok(partnerUseCase.createClient(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<PartnerClientRow>>> findClients(long companyId,
                                                                                    PartnerClientFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(partnerUseCase.findClients(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<BrandingResponse>> findBranding(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(partnerUseCase.findBrandingByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<BrandingResponse>> findPublicBranding(String domain) {
        return ResponseEntity.ok(ResponseData.ok(partnerUseCase.findBrandingByDomain(domain)));
    }
}
