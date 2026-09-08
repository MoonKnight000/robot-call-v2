package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * A post-call evaluation criterion assessed by AI upon call completion.
 * One thing the call is scored on afterwards.
 *
 * @param id       unique criterion identifier (e.g. "human_follow_up_needed")
 * @param name     human-readable label
 * @param criteria evaluation prompt or question to verify against conversation transcript
 */
public record DataEvaluationCriterion(
        String id,
        String name,
        String criteria
) {
}
