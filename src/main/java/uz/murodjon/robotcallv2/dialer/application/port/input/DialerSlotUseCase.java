package uz.murodjon.robotcallv2.dialer.application.port.input;

/**
 * The dialer's concurrency slots. A slot is reserved when a target is dispatched and
 * released exactly once, by whoever ends the call.
 */
public interface DialerSlotUseCase {

    void release(long companyId, long campaignId);
}
