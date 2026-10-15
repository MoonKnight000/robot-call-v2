package uz.murodjon.robotcallv2.dialer.application.port.input;

import uz.murodjon.robotcallv2.dialer.application.dto.TrunkCapacityRow;

import java.util.List;

/**
 * Learns a SIP trunk's channel count from the calls placed over it. Both record methods are
 * called before the call's trunk slot is released, so the count they read still includes it.
 */
public interface TrunkCapacityUseCase {

    /** A dialer call over {@code sipTrunkId} connected and has now ended; a null trunk is ignored. */
    void recordConnectedCall(Long sipTrunkId);

    /**
     * A dialer call over {@code sipTrunkId} was never answered, for {@code hangupCause}. Only
     * the "no channel / congestion" causes teach anything about capacity; the rest are ignored.
     */
    void recordUnansweredCall(Long sipTrunkId, Integer hangupCause);

    /** Every enabled trunk of the company, with its limit and what has been learned about it. */
    List<TrunkCapacityRow> findTrunkCapacities(long companyId);
}
