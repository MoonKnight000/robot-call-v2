package uz.murodjon.robotcallv2.quality.application.port.input;

import uz.murodjon.robotcallv2.quality.application.dto.ReviewScorecardRequest;
import uz.murodjon.robotcallv2.quality.application.dto.SaveScorecardCriterionRequest;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardFilter;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterionFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/** Operator call scorecards (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.3): the criteria and the calls marked on them. */
public interface ScorecardUseCase {

    ScorecardCriterion createCriterion(long companyId, SaveScorecardCriterionRequest request);

    ScorecardCriterion updateCriterion(long companyId, long id, SaveScorecardCriterionRequest request);

    void deleteCriterion(long companyId, long id);

    ScorecardCriterion requireCriterion(long companyId, long id);

    PageableData<ScorecardCriterion> listCriteria(long companyId, ScorecardCriterionFilter filter);

    CallScorecard requireCallScorecard(long companyId, long callId);

    /** The scored calls — the review inbox with {@code status = PENDING}. */
    PageableData<CallScorecard> filterCallScorecards(long companyId, CallScorecardFilter filter);

    /** A supervisor's verdict on a scored call: the judge confirmed, or its answers corrected and the call re-scored. */
    CallScorecard recordReview(long companyId, long callId, ReviewScorecardRequest request);

    /**
     * Marks a finished operator call on the company's enabled criteria — from the post-call
     * pipeline — and puts it in the review inbox, with a notification, when it scores under
     * the company's threshold or the client was upset.
     *
     * @return the stored scorecard, whose tokens are charged to the call; null when nothing
     *         was stored — no criteria, no transcript, the judge off or failed. A failure is
     *         logged, never thrown
     */
    CallScorecard recordCallScorecard(long companyId, long callId, Long operatorUserId, String transcript);

    /** The retention sweep's share: evidence quotes of calls whose transcript is gone. See {@code CallScorecardRepository}. */
    int deleteEvidenceWithoutTranscript(int batchSize);
}
