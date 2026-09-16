package uz.murodjon.robotcallv2.widget.domain.enums;

/** What the shared call counter answered to one widget call's check-and-book. */
public enum WidgetBookingResult {

    /** Both limits had room; the call now counts against them. */
    BOOKED,

    /** The agent already has as many live widget sessions as it allows; nothing was counted. */
    CONCURRENT_LIMIT_REACHED,

    /** The agent has taken as many widget calls today as it allows; nothing was counted. */
    DAILY_LIMIT_REACHED,

    /** The counter could not be reached, so nobody knows whether there is room. */
    UNAVAILABLE
}
