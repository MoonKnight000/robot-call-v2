package uz.murodjon.robotcallv2.missedcall.domain.enums;

/** Where a missed call is on the operators' list. */
public enum MissedCallStatus {
    NEW,
    /** Someone is calling back right now. */
    IN_PROGRESS,
    /** Called back and reached. */
    CALLED_BACK,
    /** Closed without a callback (the caller rang again and was served, or it needed none). */
    RESOLVED;

    /** Still on the list — a new call from the same number adds to this row instead of opening another. */
    public boolean open() {
        return this == NEW || this == IN_PROGRESS;
    }
}
