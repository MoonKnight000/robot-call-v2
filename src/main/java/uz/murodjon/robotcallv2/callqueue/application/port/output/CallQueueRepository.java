package uz.murodjon.robotcallv2.callqueue.application.port.output;

import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueFilter;

import java.util.List;

public interface CallQueueRepository {

    long create(long companyId, CallQueue queue);

    void update(long companyId, long id, CallQueue queue);

    void delete(long companyId, long id);

    CallQueue find(long companyId, long id);

    List<CallQueue> findAll(long companyId, CallQueueFilter filter);

    long count(long companyId);

    List<CallQueue> findEnabledByCompanyId(long companyId);
}
