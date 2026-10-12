package uz.murodjon.robotcallv2.siptrunk.application.port.input;

import uz.murodjon.robotcallv2.siptrunk.application.dto.*;

import java.util.List;

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

    String acquireCallerId(long companyId, long poolId);

    void recordCallAnswer(long poolId, String phone);
}
