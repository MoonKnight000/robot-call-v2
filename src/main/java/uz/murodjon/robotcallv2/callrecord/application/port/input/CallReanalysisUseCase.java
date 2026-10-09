package uz.murodjon.robotcallv2.callrecord.application.port.input;

import uz.murodjon.robotcallv2.callrecord.application.dto.CallReanalysisPreviewResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.CreateCallReanalysisRequest;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisFilter;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/**
 * Runs an agent's current analysis settings over its past calls (a {@link CallReanalysisJob}):
 * a new {@code dataNeeded} field or a changed evaluation criterion reaches the calls made
 * before it existed.
 */
public interface CallReanalysisUseCase {

    /** How many calls the request covers and what it would roughly cost. Queues nothing. */
    CallReanalysisPreviewResponse previewReanalysis(long companyId, CreateCallReanalysisRequest request);

    /** Queues the job; the worker goes through the calls in the background. */
    CallReanalysisJob createReanalysis(long companyId, CreateCallReanalysisRequest request);

    CallReanalysisJob findReanalysis(long companyId, long id);

    PageableData<CallReanalysisJob> filterReanalyses(long companyId, CallReanalysisFilter filter);

    /** Stops a queued or running job; the calls already done keep their new analysis. */
    CallReanalysisJob cancelReanalysis(long companyId, long id);
}
