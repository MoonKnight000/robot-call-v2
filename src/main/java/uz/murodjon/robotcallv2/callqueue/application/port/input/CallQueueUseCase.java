package uz.murodjon.robotcallv2.callqueue.application.port.input;

import uz.murodjon.robotcallv2.callqueue.application.dto.SaveCallQueueRequest;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface CallQueueUseCase {

    CallQueue create(long companyId, SaveCallQueueRequest request);

    CallQueue update(long companyId, long id, SaveCallQueueRequest request);

    void delete(long companyId, long id);

    CallQueue requireQueue(long companyId, long id);

    /** The queue, or null — what a live call asks. */
    CallQueue findQueue(long companyId, long id);

    PageableData<CallQueue> list(long companyId, CallQueueFilter filter);

    List<CallQueue> findEnabledByCompanyId(long companyId);
}
