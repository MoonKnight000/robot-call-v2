package uz.murodjon.robotcallv2.agent.telephony;

/**
 * How a supervisor reaches the call they asked to watch — by phone or from the browser,
 * whichever the call's engine offers.
 *
 * @param channelId the channel ringing the supervisor's phone; null when they join from the browser
 * @param join      what the browser joins the call's room with; null when their phone rings
 */
public record SupervisorAccess(String channelId, WebRtcJoin join) {

    public static SupervisorAccess ringing(String channelId) {
        return new SupervisorAccess(channelId, null);
    }

    public static SupervisorAccess browser(WebRtcJoin join) {
        return new SupervisorAccess(null, join);
    }
}
