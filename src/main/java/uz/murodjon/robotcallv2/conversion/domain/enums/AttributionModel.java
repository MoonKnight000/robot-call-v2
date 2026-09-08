package uz.murodjon.robotcallv2.conversion.domain.enums;

/** Which call inside the window gets the credit when several could claim it. */
public enum AttributionModel {

    /**
     * The most recent answered call before the conversion. The default: on a
     * debt-collection list the call that finally moved someone is nearly always the last
     * one they took.
     */
    LAST_CALL,

    /**
     * The earliest answered call in the window. Worth choosing when the first contact is
     * what does the work and later calls only chase a decision already made.
     */
    FIRST_CALL
}
