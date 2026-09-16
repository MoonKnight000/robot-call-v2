package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Where a published agent snapshot stands: {@code PUBLISHED} is the one version calls run
 * on, {@code ARCHIVED} every earlier one — kept so a rollback has something to return to.
 */
public enum AgentVersionStatus {
    PUBLISHED,
    ARCHIVED
}
