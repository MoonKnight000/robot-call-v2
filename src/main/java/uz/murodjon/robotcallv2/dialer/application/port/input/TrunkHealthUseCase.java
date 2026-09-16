package uz.murodjon.robotcallv2.dialer.application.port.input;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

/** What the dialer learns about a SIP trunk from the calls placed over it. */
public interface TrunkHealthUseCase {

    /** A call over {@code trunkId} ended this way; a null trunk (none chosen) is ignored. */
    void recordOutcome(Long trunkId, Disposition disposition);
}
