package uz.murodjon.robotcallv2.siptrunk.application.port.output;

import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPool;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Output persistence port for Caller ID pools.
 */
public interface CallerIdPoolRepository {

    CallerIdPool save(long companyId, CallerIdPool pool);

    Optional<CallerIdPool> findById(long companyId, long id);

    List<CallerIdPool> findAllByCompanyId(long companyId);

    void delete(long companyId, long id);

    CallerIdPoolNumber addNumber(long companyId, long poolId, String phone, String label);

    void removeNumber(long companyId, long poolId, long numberId);

    void updateNumberStatus(long companyId, long poolId, long numberId, String status, Instant coolingUntil);

    void recordNumberUsage(long poolId, long numberId, Instant usedAt);

    void recordNumberAnswer(long poolId, String phone);
}
