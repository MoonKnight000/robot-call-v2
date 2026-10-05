package uz.murodjon.robotcallv2.report.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.report.application.dto.*;
import uz.murodjon.robotcallv2.report.domain.entity.*;

/** Domain → DTO for the report endpoints. */
@Component
public class ReportMapper {

    public CallRow toCallRow(ReportedCall call) {
        return new CallRow(call.callId(), call.targetId(), call.phone(), call.language(), call.startedAt(),
                call.endedAt(), call.durationSec(), call.disposition(), call.hangupCause(), call.hasRecording(),
                call.summary(), call.promisedDate(), call.promisedAmount(), call.crmNoteId(), call.clientName(),
                call.campaignName(), call.operatorName());
    }

    public CallDetail toCallDetail(CallReport report) {
        return new CallDetail(toCallRow(report.call()), report.transcript(), report.reasonCode(), report.sentiment(),
                report.needsFollowUp(), report.followUpNote(), report.escalated(), report.errorMessage(),
                report.technical());
    }

    public CampaignComparisonRow toCampaignComparisonRow(CampaignComparison comparison) {
        return new CampaignComparisonRow(comparison.campaignId(), comparison.campaignName(), comparison.totalCalls(),
                comparison.answeredCalls(), comparison.answerRate(), comparison.avgDurationSec(),
                comparison.promises());
    }

    public QueueReportRow toQueueReportRow(QueuePerformance queue) {
        return new QueueReportRow(queue.queueId(), queue.queueName(), queue.offered(), queue.answered(),
                queue.abandoned(), queue.shortAbandoned(), queue.answeredWithinSla(), queue.serviceLevelPercent(),
                queue.abandonRatePercent(), queue.averageWaitSeconds(), queue.maxWaitSeconds(),
                queue.averageTalkSeconds(), queue.overflowed(), queue.callbacksRequested(),
                queue.averageSurveyScore());
    }

    public OperatorReportRow toOperatorReportRow(OperatorPerformance operator) {
        return new OperatorReportRow(operator.userId(), operator.name(), operator.answered(), operator.missed(),
                operator.averageTalkSeconds(), operator.totalTalkSeconds(), operator.averageSurveyScore(),
                operator.availableSeconds(), operator.onCallSeconds(), operator.wrapUpSeconds(),
                operator.breakSeconds(), operator.occupancyPercent());
    }
}
