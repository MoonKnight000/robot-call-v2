package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.report.application.dto.RoiReportResponse;
import uz.murodjon.robotcallv2.report.application.port.input.RoiReportUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class RoiReportControllerImpl implements RoiReportController {

    private final RoiReportUseCase roiReportUseCase;

    public RoiReportControllerImpl(RoiReportUseCase roiReportUseCase) {
        this.roiReportUseCase = roiReportUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<RoiReportResponse>> roi(long companyId, String from, String to,
                                                               Long campaignId, Long operatorHourlyCostUzs) {
        return ResponseEntity.ok(ResponseData.ok(
                roiReportUseCase.findRoiReport(companyId, from, to, campaignId, operatorHourlyCostUzs)));
    }
}
