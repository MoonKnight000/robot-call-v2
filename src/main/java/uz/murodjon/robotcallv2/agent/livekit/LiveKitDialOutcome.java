package uz.murodjon.robotcallv2.agent.livekit;

/**
 * How an outbound LiveKit SIP call ended its ringing.
 *
 * @param sipStatus the final SIP response when it was not answered (486, 603, 408 ...);
 *                  0 when answered or when LiveKit did not say
 */
public record LiveKitDialOutcome(boolean answered, int sipStatus) {

    public static LiveKitDialOutcome answeredCall() {
        return new LiveKitDialOutcome(true, 0);
    }

    public static LiveKitDialOutcome unanswered(int sipStatus) {
        return new LiveKitDialOutcome(false, sipStatus);
    }

    /**
     * Whether the trunk, not the callee, turned the call down (a 5xx from the carrier, which is
     * also what a trunk over its calls-per-second limit answers) — the case for trying the next trunk.
     */
    public boolean failedAtTrunk() {
        return !answered && (sipStatus == 500 || sipStatus == 502 || sipStatus == 503 || sipStatus == 504);
    }
}
