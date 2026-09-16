package uz.murodjon.robotcallv2.agent.stt;

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
 * <p>On, and the risk it carries is worth restating: faster endpointing was tried at the
 * provider level first (see {@code YandexSttProperties#eouSensitivity}), where it bought a
 * few hundred milliseconds and left uz-UZ finals arriving as fragments the model answered
 * as if they were whole sentences. This takes the same risk from a different angle, so a
 * change here is worth making only while listening to what the bot actually answers.
 *
 * <p>Only takes effect when {@code vad-gating} is on as well — there is no gate to close
 * otherwise, and the whole block goes quietly inert
 * ({@code AudioPipelineAssembler#buildAudioListeners}).
 *
 * @param enabled          master switch. Off leaves the provider's own detector in
 *                         charge: Yandex stops being switched to
 *                         {@code ExternalEouClassifier} and reads its own pause hint again
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
 * @param finalGraceMs     how long after this side declares the utterance over the
 *                         provider's final is waited for before the last interim is taken
 *                         as the final instead ({@code SttStreamBridge}). 0 always waits.
 *                         On recorded calls (2026-09-05) the final came back 500–1100 ms
 *                         after the EOU and said what the last interim already said — the
 *                         provider's own final, when it does arrive late, is dropped.
 *                         Watch {@code voice_stt_finals_promoted_total} and the "late
 *                         final differed" log lines: a stream of those means the interims
 *                         here lag the audio
 * @param completeSilenceMs silence that ends an utterance whose last interim reads as a
 *                         finished sentence ({@code TranscriptTurnCues}) — shorter than
 *                         the post-roll, which stays the budget for a caller whose words
 *                         say they may still be mid-thought. 0 leaves the words unable
 *                         to shorten the wait.
 *                         <p>The words move the wait because a timer cannot: C.4 showed
 *                         finished and unfinished pauses overlap between 1.1 and 1.8 s,
 *                         where no threshold can tell them apart — but in a verb-final
 *                         language the sentence can. A last interim that has reached its
 *                         verb ("to'layman", "bo'ladi", "bilmadim", "ha/yo'q", "нет",
 *                         "заплачу") closes after this much silence; one that has not
 *                         ("pulni", "bankga", "lekin", "besh yuz", "потому что", a bare
 *                         number) waits the full post-roll PLUS the turn extension
 *                         ({@code turn.max-extend-ms}); a neutral ending keeps the
 *                         post-roll. This is the one lever that moves cut-offs and latency
 *                         in opposite directions at once.
 *                         <p>800: below it the recognizer's interim lags the audio too
 *                         often to have the last word yet. Raise it toward the post-roll
 *                         if stitched turns ({@code voice_dialog_turn_stitched}) climb —
 *                         those are exactly the finished-sounding sentences that were not
 */
public record EndpointingProperties(
        boolean enabled,
        int shortUtteranceMs,
        int shortSilenceMs,
        int maxUtteranceMs,
        DynamicEndpointingProperties dynamic,
        int finalGraceMs,
        int completeSilenceMs
) {
}
