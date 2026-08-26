package uz.murodjon.uysotvoice.agent.stt;

/**
 * Client-side end-of-utterance detection: instead of waiting for SpeechKit's own
 * detector to decide the caller has finished, the VAD that already runs for barge-in
 * decides, and the recognizer is told outright ({@code ExternalEouClassifier} +
 * an explicit EOU event).
 *
 * <p>The point is that not every answer deserves the same wait. "Ha" and a slowly
 * dictated contract number end the same way — a pause — and one detector has to be set
 * conservatively enough for the second, which makes every short answer pay for it. Here
 * a short answer is cut off after {@code shortSilenceMs} while anything longer keeps
 * the full {@code vad-gating.post-roll-ms}, so the aggressive path only ever applies to
 * utterances too short to be worth splitting.
 *
 * <p><b>Off by default, and it belongs off until real uz-UZ calls say otherwise.</b>
 * Faster endpointing has already been tried at the provider level here (see the
 * {@code eou-sensitivity} note in application.yml): it did buy a few hundred
 * milliseconds and it did leave uz-UZ finals arriving as fragments the model then
 * answered as if they were whole sentences. This takes the same risk from a different
 * angle, so it is worth turning on only while listening to what the bot answers.
 *
 * @param enabled          master switch. Off leaves the provider's own detector in
 *                         charge, exactly as before
 * @param shortUtteranceMs speech up to this long counts as a short answer. Above it the
 *                         caller is saying something, and the conservative wait applies
 * @param shortSilenceMs   silence that ends a short answer. This is the whole saving:
 *                         it replaces the post-roll, not adds to it
 * @param maxUtteranceMs   safety net. With an external classifier nothing but us ever
 *                         ends an utterance, so a VAD that fails mid-call (or a line
 *                         with constant noise) would otherwise leave the recognizer
 *                         holding a turn that never finalizes and a caller talking to a
 *                         bot that never answers
 * @param dynamic          lets the long-utterance wait learn the caller's rhythm instead of
 *                         staying at the one {@code post-roll-ms} configured for everyone
 */
public record EndpointingProperties(
        boolean enabled,
        int shortUtteranceMs,
        int shortSilenceMs,
        int maxUtteranceMs,
        DynamicEndpointingProperties dynamic
) {
}
