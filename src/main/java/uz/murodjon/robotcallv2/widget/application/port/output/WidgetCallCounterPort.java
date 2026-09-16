package uz.murodjon.robotcallv2.widget.application.port.output;

import uz.murodjon.robotcallv2.widget.domain.entity.WidgetCallBooking;
import uz.murodjon.robotcallv2.widget.domain.enums.WidgetBookingResult;

/**
 * The widget call counts every application instance shares: each agent's live sessions
 * and its calls of the day.
 */
public interface WidgetCallCounterPort {

    /**
     * Checks both of the booking's limits and, only when both have room, counts it — as one
     * step, so two requests arriving together cannot both take the last line.
     *
     * @return {@link WidgetBookingResult#UNAVAILABLE} when the counter cannot be reached; never throws
     */
    WidgetBookingResult book(WidgetCallBooking booking);

    /**
     * Hands a booking back — the session it was for could not be prepared — so it holds no
     * line and does not spend the day's allowance. Best effort: a failure is logged, and the
     * line frees itself when the booking expires.
     */
    void release(WidgetCallBooking booking);
}
