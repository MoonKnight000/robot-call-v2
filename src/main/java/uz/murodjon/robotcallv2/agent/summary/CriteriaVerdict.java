package uz.murodjon.robotcallv2.agent.summary;

/**
 * Whether a simulated call met a test case's success criteria ({@link CallQualityJudge#checkCriteria}).
 *
 * @param met  every criterion holds in the transcript
 * @param note one sentence on the criterion that failed; null when all held
 */
public record CriteriaVerdict(boolean met, String note) {
}
