package uz.murodjon.robotcallv2.alert.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.alert.application.dto.SaveAlertRuleRequest;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;

@Component
public class AlertRuleMapper {

    private static final int DEFAULT_MIN_CALLS = 10;
    private static final int DEFAULT_COOLDOWN_MINUTES = 60;

    public AlertRule toAlertRule(long companyId, SaveAlertRuleRequest request) {
        return AlertRule.settings(
                companyId,
                request.name().trim(),
                request.metric(),
                request.comparator(),
                request.threshold(),
                request.windowMinutes(),
                request.minCalls() != null ? request.minCalls() : DEFAULT_MIN_CALLS,
                request.cooldownMinutes() != null ? request.cooldownMinutes() : DEFAULT_COOLDOWN_MINUTES,
                request.aiAgentId(),
                request.enabled() == null || request.enabled());
    }
}
