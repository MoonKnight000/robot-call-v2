package uz.murodjon.robotcallv2.dialer.application.port.input;

import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;

/**
 * The dialer's originated calls, from origination until the channel ends. {@link #remove}
 * is the single ownership transfer: whoever removes an entry owns releasing its slot.
 */
public interface OutboundCallTrackingUseCase {

    void register(String channelId, OutboundCall call);

    /** The call for {@code channelId} without removing it; {@code null} when not tracked. */
    OutboundCall peek(String channelId);

    /** The channel entered Stasis (answered), so the unanswered-call sweep leaves it alone. */
    void markAnswered(String channelId);

    /** Removes and returns the call, or {@code null} if it is not an outbound call tracked here. */
    OutboundCall remove(String channelId);

    /** Removes and returns the call only if it never answered; {@code null} otherwise. */
    OutboundCall removeIfUnanswered(String channelId);
}
