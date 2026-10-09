package uz.murodjon.robotcallv2.quality.application.port.output;

import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface CallScorecardRepository {

    /** Replaces the call's scorecard if it has one. */
    CallScorecard upsert(long companyId, CallScorecard scorecard);

    /** Writes a supervisor's review: the score, the review fields and each answer's current value, by id. */
    CallScorecard updateReview(long companyId, CallScorecard scorecard);

    /** Null when the call was never scored or belongs to another company. */
    CallScorecard findByCompanyIdAndCallId(long companyId, long callId);

    PageableData<CallScorecard> findPageByCompanyId(long companyId, CallScorecardFilter filter);

    /**
     * Clears the evidence quotes of calls whose transcript the retention sweep has deleted —
     * the quotes are the client's words, and keep no longer than the rest of them. Across
     * companies, at most {@code batchSize} answers; returns how many were cleared.
     */
    int deleteEvidenceWithoutTranscript(int batchSize);
}
