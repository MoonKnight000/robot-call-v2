package uz.murodjon.robotcallv2.siptrunk.application.port.input;

import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;

import java.util.List;
import java.util.Set;

/**
 * Use case interface for managing Caller ID pools and number rotation.
 */
public interface CallerIdPoolUseCase {

    CallerIdPoolDetail createPool(long companyId, CreateCallerIdPoolRequest request);

    CallerIdPoolDetail updatePool(long companyId, long poolId, UpdateCallerIdPoolRequest request);

    CallerIdPoolDetail getPool(long companyId, long poolId);

    List<CallerIdPoolRow> listPools(long companyId);

    void deletePool(long companyId, long poolId);

    CallerIdNumberRow addNumber(long companyId, long poolId, AddCallerIdNumberRequest request);

    void removeNumber(long companyId, long poolId, long numberId);

    void setNumberCooling(long companyId, long poolId, long numberId, int coolingHours);

    void activateNumber(long companyId, long poolId, long numberId);

    void disableNumber(long companyId, long poolId, long numberId);

    /**
     * The pool's next number whose own trunk is one of {@code trunkIds}, with its use recorded — the
     * one closest to {@code calleePhone}'s own when that is given (local presence). Null when no such
     * number can be dialled from now; the call then waits rather than going out under another number.
     */
    CallerIdPoolNumber acquireCallerIdOnTrunks(long companyId, long poolId, String calleePhone, Set<Long> trunkIds);

    void recordCallAnswer(long poolId, String phone);
}
