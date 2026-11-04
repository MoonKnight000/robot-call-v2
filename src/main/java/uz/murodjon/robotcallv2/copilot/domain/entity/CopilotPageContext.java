package uz.murodjon.robotcallv2.copilot.domain.entity;

import jakarta.validation.constraints.Pattern;

/**
 * Where in the panel the user asked from, so "shu agent" and "bu qo'ng'iroq" mean something.
 * Only ids — the copilot reads the objects through its tools, which check the company, so a
 * forged id here opens nothing.
 *
 * @param page a short page key the frontend sends ("agent-editor", "campaign", "call-detail", ...)
 */
public record CopilotPageContext(
        @Pattern(regexp = "[a-z0-9-]{1,40}") String page,
        Long agentId,
        Long campaignId,
        Long callId,
        Long scenarioId
) {

    public boolean isEmpty() {
        return page == null && agentId == null && campaignId == null && callId == null && scenarioId == null;
    }
}
