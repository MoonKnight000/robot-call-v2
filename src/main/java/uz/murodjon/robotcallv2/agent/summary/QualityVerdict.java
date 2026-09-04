package uz.murodjon.robotcallv2.agent.summary;

/**
 * What a second model made of how one call was handled — the rubric behind
 * {@link CallQualityJudge}.
 *
 * <p>Deliberately not a single number. A call can score well on politeness and still have
 * quoted a sum that was never in the file, and the score would hide it; each flag below is
 * a separate thing that either happened or did not, so a regression shows up as that flag
 * rising rather than as an average drifting.
 *
 * @param score              overall handling, 0–100. Only useful as a trend
 * @param guardrailViolation the agent stated a figure, a date or a commitment the facts do
 *                           not support, or broke a scenario rule. The one flag that is
 *                           never acceptable (§4.4)
 * @param languageMismatch   the agent answered in a language the caller was not speaking
 * @param talkedOverClient   the agent spoke over the caller, or answered before they had
 *                           finished — what an endpointing setting that is too aggressive
 *                           looks like from the transcript
 * @param outcomeCorrect     the disposition the call was filed under matches what the
 *                           transcript actually shows happened
 * @param note               one sentence on the worst thing in the call, for the log
 */
public record QualityVerdict(
        int score,
        boolean guardrailViolation,
        boolean languageMismatch,
        boolean talkedOverClient,
        boolean outcomeCorrect,
        String note
) {

    /** Whether anything here is worth a human looking at the recording. */
    public boolean needsReview() {
        return guardrailViolation || languageMismatch || talkedOverClient || !outcomeCorrect;
    }
}
