package uz.murodjon.robotcallv2.quality.application.port.output;

import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.CallReviewFilter;
import uz.murodjon.robotcallv2.quality.domain.entity.ScoredAgent;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CallQualityRepository {

    /** Inserts a row without an id, replaces the one with it. */
    CallQuality save(CallQuality quality);

    /** The call's row, or null when it was never scored or belongs to another company. */
    CallQuality findByCompanyIdAndCallAttemptId(long companyId, long callAttemptId);

    PageableData<CallQuality> findPageByCompanyId(long companyId, CallReviewFilter filter);

    /** Sets {@code repeatCall} on those calls' rows; calls without a row are skipped. */
    void markRepeatCalls(long companyId, Collection<Long> callAttemptIds);

    /** Every agent with rows since {@code since}, across companies (UNIVERSAL-OPERATOR-PLAN D.3). */
    List<ScoredAgent> findAgentsScoredSince(Instant since);

    List<CallQuality> findByAgentSince(long companyId, long agentId, Instant since);
}
