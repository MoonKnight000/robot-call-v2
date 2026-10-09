package uz.murodjon.robotcallv2.callqueue.infrastructure.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callqueue.application.port.output.CallQueueRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CallQueueLookupPort;

/**
 * Answers the campaign side's "is this queue the company's?" from the queue repository.
 * The repository rather than {@code CallQueueService}: that service reads campaigns itself,
 * and going through it would tie the two beans into a construction cycle.
 */
@Component
public class CallQueueLookupAdapter implements CallQueueLookupPort {

    private final CallQueueRepository repository;

    public CallQueueLookupAdapter(CallQueueRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByCompanyIdAndId(long companyId, long queueId) {
        return repository.find(companyId, queueId) != null;
    }
}
