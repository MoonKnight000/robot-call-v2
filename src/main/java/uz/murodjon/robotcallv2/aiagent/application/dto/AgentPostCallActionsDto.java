package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.PostCallAction;

import java.util.List;

/**
 * DTO for Post-Call Actions & Automated Follow-ups tab.
 */
public record AgentPostCallActionsDto(
        List<PostCallAction> actions
) {
    public List<PostCallAction> actionsOrEmpty() {
        return actions != null ? actions : List.of();
    }
}
