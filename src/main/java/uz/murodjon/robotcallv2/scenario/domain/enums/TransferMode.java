package uz.murodjon.robotcallv2.scenario.domain.enums;

/** How a {@code CALL_TRANSFER} stage hands the caller over. */
public enum TransferMode {
    /** The caller is connected straight to the number. */
    COLD,
    /** The agent first tells the person who answers what the call is about, then connects the caller. */
    WARM
}
