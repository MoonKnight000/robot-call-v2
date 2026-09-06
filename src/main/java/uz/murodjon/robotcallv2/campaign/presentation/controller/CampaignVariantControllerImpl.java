package uz.murodjon.robotcallv2.campaign.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.presentation.dto.AbTestReportResponse;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantCreateRequest;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantResponse;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantUpdateRequest;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class CampaignVariantControllerImpl implements CampaignVariantController {

    private final CampaignVariantUseCase variantUseCase;

    public CampaignVariantControllerImpl(CampaignVariantUseCase variantUseCase) {
        this.variantUseCase = variantUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CampaignVariantResponse>> create(long campaignId, CampaignVariantCreateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.create(campaignId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignVariantResponse>> get(long campaignId, long variantId) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.get(campaignId, variantId)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignVariantResponse>> update(long campaignId, long variantId, CampaignVariantUpdateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.update(campaignId, variantId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long campaignId, long variantId) {
        variantUseCase.delete(campaignId, variantId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<CampaignVariantResponse>>> list(long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.list(campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<AbTestReportResponse>> getReport(long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(variantUseCase.getReport(campaignId)));
    }
}
