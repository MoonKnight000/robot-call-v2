package uz.murodjon.robotcallv2.campaign.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.campaign.application.dto.AbTestReportResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantCreateRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantUpdateRequest;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class CampaignVariantControllerImpl implements CampaignVariantController {

    private final CampaignVariantUseCase variantUseCase;

    public CampaignVariantControllerImpl(CampaignVariantUseCase variantUseCase) {
        this.variantUseCase = variantUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CampaignVariantResponse>> create(long companyId, long campaignId, CampaignVariantCreateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.create(companyId, campaignId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignVariantResponse>> get(long companyId, long campaignId, long variantId) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.get(companyId, campaignId, variantId)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignVariantResponse>> update(long companyId, long campaignId, long variantId,
            CampaignVariantUpdateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.update(companyId, campaignId, variantId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long campaignId, long variantId) {
        variantUseCase.delete(companyId, campaignId, variantId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<CampaignVariantResponse>>> list(long companyId, long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.list(companyId, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<AbTestReportResponse>> getReport(long companyId, long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.getReport(companyId, campaignId)));
    }
}
