package uz.murodjon.robotcallv2.callqueue.application.port.input;

import uz.murodjon.robotcallv2.callqueue.application.dto.CallQueueLiveRow;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.util.List;

public interface CallQueueLiveUseCase {

    /** Every enabled queue of the company as it stands this second. */
    List<CallQueueLiveRow> findLive(long companyId);

    /** The same, pushed every couple of seconds while the wallboard is open. */
    EventSubscription subscribe(long companyId, EventSink sink);
}
