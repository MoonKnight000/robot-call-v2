package uz.murodjon.robotcallv2.agent.rtp;

/**
 * One finished call's inbound audio quality, as it is stored on the call ("Texnik" tab).
 *
 * @param packetsReceived packets that arrived
 * @param packetsLost     packets the sequence numbers say never did
 * @param jitterMs        smoothed interarrival jitter, rounded to whole milliseconds
 * @param mosEstimate     {@link MosEstimator} score, or null for a call that received
 *                        nothing — a silent media path has no quality, it has a fault
 */
public record RtpQuality(long packetsReceived, long packetsLost, int jitterMs, Double mosEstimate) {
}
