package uz.murodjon.robotcallv2.agent.stt;

/**
 * Lets the end-of-utterance wait learn this caller's rhythm instead of staying at the one
 * value that was configured for all of them.
 *
 * <p>{@code post-roll-ms} has to be set for the slowest caller on the list — someone who
 * pauses mid-sentence to think — and every brisk caller then pays that wait on every turn.
 * A person who answers without pausing gives the same evidence every time, and after a few
 * turns there is no reason to keep waiting on them as if they might not have finished.
 *
 * <p>What is measured is the one thing that says the wait was wrong: the gate shut and the
 * caller kept talking. That pause was mid-utterance, so the wait grows back towards
 * {@code post-roll-ms}. A close that nothing followed was a real end of turn, so it shrinks
 * towards {@link #minPostRollMs()}. Only the long-utterance wait moves — a short answer
 * keeps {@code EndpointingProperties.shortSilenceMs}, which is already the aggressive path.
 *
 * <p>Nothing here is language-specific, which is the point: the semantic turn detectors
 * worth having do not cover uz-UZ, and this does.
 *
 * <p><b>Off by default.</b> It only ever shortens a wait that {@code post-roll-ms} had set
 * conservatively, so the failure mode is the familiar one — utterances split in two — and
 * it belongs off until real calls say the adaptation lands where it should.
 *
 * @param enabled       master switch. Off leaves the fixed {@code post-roll-ms} in charge
 * @param minPostRollMs floor the wait may shrink to. Below this the recognizer stops
 *                      getting enough silence to be sure the caller has stopped at all
 * @param emaAlpha      how much one utterance moves the average (0..1). Small values need
 *                      several turns to adapt and survive one odd pause; large values track
 *                      the last turn or two and swing
 * @param reopenGraceMs how soon after a close speech has to resume for the close to count
 *                      as premature. Too long and a caller answering the bot's next
 *                      question is mistaken for one who never finished the last one
 */
public record DynamicEndpointingProperties(
        boolean enabled,
        int minPostRollMs,
        double emaAlpha,
        int reopenGraceMs
) {
}
