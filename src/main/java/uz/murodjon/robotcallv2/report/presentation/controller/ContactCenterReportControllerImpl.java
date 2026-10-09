package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.report.application.dto.OperatorReportRow;
import uz.murodjon.robotcallv2.report.application.dto.QueueReportRow;
import uz.murodjon.robotcallv2.report.application.port.input.ContactCenterReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.PredictiveDialingStats;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class ContactCenterReportControllerImpl implements ContactCenterReportController {

    private final ContactCenterReportUseCase contactCenterReportUseCase;

    public ContactCenterReportControllerImpl(ContactCenterReportUseCase contactCenterReportUseCase) {
        this.contactCenterReportUseCase = contactCenterReportUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<QueueReportRow>>> queues(long companyId, String from, String to) {
        return ResponseEntity.ok(ResponseData.ok(contactCenterReportUseCase.findQueueReport(companyId, from, to)));
    }

    @Override
    public ResponseEntity<ResponseData<List<OperatorReportRow>>> operators(long companyId, String from, String to) {
        return ResponseEntity.ok(ResponseData.ok(contactCenterReportUseCase.findOperatorReport(companyId, from, to)));
    }

    @Override
    public ResponseEntity<ResponseData<PredictiveDialingStats>> predictiveDialing(long companyId, long campaignId,
                                                                                String from, String to) {
        return ResponseEntity.ok(ResponseData.ok(
                contactCenterReportUseCase.findPredictiveDialingStats(companyId, campaignId, from, to)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CallEvent>>> callEvents(long companyId, long callId) {
        return ResponseEntity.ok(ResponseData.ok(contactCenterReportUseCase.findCallEvents(companyId, callId)));
    }
}
