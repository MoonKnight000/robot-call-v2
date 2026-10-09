package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisFilter;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;

public interface CallReanalysisJobRepository {

    CallReanalysisJob create(CallReanalysisJob job);

    CallReanalysisJob findByCompanyIdAndId(long companyId, long id);

    PageableData<CallReanalysisJob> findPageByCompanyId(long companyId, CallReanalysisFilter filter);

    /** Whether the company has a job {@code QUEUED} or {@code RUNNING}. */
    boolean existsActiveByCompanyId(long companyId);

    /** The oldest {@code QUEUED} or {@code RUNNING} job of any company; null when there is none. */
    CallReanalysisJob findNextActive();

    /**
     * Writes the worker's progress — status, counters, cursor, timestamps — unless the job is no
     * longer active; false then: it was cancelled while the batch ran.
     */
    boolean updateProgress(CallReanalysisJob job);

    /** Cancels an active job; false when it is not active (finished, or already cancelled). */
    boolean cancel(long companyId, long id, Instant finishedAt);
}
