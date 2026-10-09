package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Where a published agent snapshot stands: {@code PUBLISHED} is the one version calls run
 * on, {@code ARCHIVED} every earlier one — kept so a rollback has something to return to.
 */
public enum AgentVersionStatus {
    PUBLISHED,
    /** Published for a rollout: runs on part of the traffic and on the staging numbers until promoted or cancelled. */
    CANDIDATE,
    ARCHIVED
}
