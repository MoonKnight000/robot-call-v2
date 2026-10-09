package uz.murodjon.robotcallv2.alert.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.infrastructure.persistence.entity.AlertRuleEntity;

@Component
public class AlertRuleEntityMapper {

    /** Copies a rule's settings onto {@code entity}; the evaluation state is the scheduler's and is not touched. */
    public void applySettings(AlertRuleEntity entity, AlertRule rule) {
        entity.setName(rule.name());
        entity.setMetric(rule.metric());
        entity.setComparator(rule.comparator());
        entity.setThreshold(rule.threshold());
        entity.setWindowMinutes(rule.windowMinutes());
        entity.setMinCalls(rule.minCalls());
        entity.setCooldownMinutes(rule.cooldownMinutes());
        entity.setAiAgentId(rule.aiAgentId());
        entity.setEnabled(rule.enabled());
    }

    public AlertRule toAlertRule(AlertRuleEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AlertRule(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getMetric(),
                entity.getComparator(),
                entity.getThreshold(),
                entity.getWindowMinutes(),
                entity.getMinCalls(),
                entity.getCooldownMinutes(),
                entity.getAiAgentId(),
                entity.isEnabled(),
                entity.getLastValue(),
                entity.getLastEvaluatedAt(),
                entity.getLastTriggeredAt(),
                entity.getCreatedAt());
    }
}
