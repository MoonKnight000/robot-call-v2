package uz.murodjon.robotcallv2.campaign.application.port.output;

/**
 * Whether an operator queue a campaign names is the company's own.
 *
 * <p>A port rather than {@code CallQueueUseCase}: the queue side already reads campaigns
 * (a queue's callback campaign), and the two features calling each other's use cases
 * would be a cycle. Implemented by {@code callqueue} ({@code CallQueueLookupAdapter}).
 */
public interface CallQueueLookupPort {

    /** @return false for a queue that does not exist or belongs to another company */
    boolean existsByCompanyIdAndId(long companyId, long queueId);
}
