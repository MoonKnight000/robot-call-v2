package uz.murodjon.robotcallv2.missedcall.application.port.input;

import uz.murodjon.robotcallv2.missedcall.application.dto.AssignMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.application.dto.MissedCallCallBackResponse;
import uz.murodjon.robotcallv2.missedcall.application.dto.UpdateMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallFilter;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallOccurrence;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface MissedCallUseCase {

    PageableData<MissedCall> list(long companyId, MissedCallFilter filter);

    MissedCall requireMissedCall(long companyId, long id);

    MissedCall assign(long companyId, long id, AssignMissedCallRequest request);

    MissedCall update(long companyId, long id, UpdateMissedCallRequest request);

    /** Rings the current operator's phone and, once they answer, dials the caller. */
    MissedCallCallBackResponse callBack(long companyId, long id);

    /** Adds an unserved call to the list — or to the caller's open row. Null for a call with no usable number. */
    MissedCall record(long companyId, MissedCallOccurrence occurrence);

    /** Closes the caller's open row, if there is one: they were reached after all. */
    void resolveByPhone(long companyId, String phone, MissedCallStatus status, Long userId);

    /** Rows whose queue calls back by itself, due for another try, oldest first. */
    List<MissedCall> findAutoCallbackCandidates(int limit);

    /**
     * Takes a candidate for one automatic callback. False when another instance took it
     * first — the caller must not be rung then.
     */
    boolean claimAutoCallback(long companyId, long id);

    void markCallbackAttempt(long id);

    /** An automatic callback is ringing {@code userId}: the row is theirs and in progress. */
    void startAutoCallback(long companyId, long id, long userId);

    /** A callback did not reach the caller: back on the list. */
    void markCallbackFailed(long companyId, long id);
}
