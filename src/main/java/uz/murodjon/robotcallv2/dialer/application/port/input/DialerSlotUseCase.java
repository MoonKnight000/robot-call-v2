package uz.murodjon.robotcallv2.dialer.application.port.input;

/**
 * The dialer's concurrency slots. A slot is reserved when a target is dispatched and
 * released exactly once, by whoever ends the call.
 */
public interface DialerSlotUseCase {

    void release(long companyId, long campaignId);

    /** Gives back the call's place on its SIP trunk; a null trunk is ignored. Called alongside {@link #release}. */
    void releaseTrunk(Long sipTrunkId);
}
