package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * How one scenario stage did over a period, read from the stage every transcript line was
 * spoken in ({@code call_transcript.dialog_state}) — where in the scenario calls are lost.
 *
 * @param stage          the scenario's {@code StageDef.id()}
 * @param reachedCalls   calls with at least one line spoken in this stage
 * @param droppedCalls   calls whose last line was spoken in this stage and that did not end
 *                       in a conversion — the call was lost here
 * @param convertedCalls calls that reached this stage and ended in a conversion
 *                       ({@code Disposition#isConversion})
 * @param conversionRate {@code convertedCalls} / {@code reachedCalls} (0..1)
 */
public record StageFunnelStep(String stage, long reachedCalls, long droppedCalls, long convertedCalls,
                              double conversionRate) {
}
