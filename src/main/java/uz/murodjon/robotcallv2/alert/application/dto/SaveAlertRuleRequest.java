package uz.murodjon.robotcallv2.alert.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertComparator;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertMetric;

/**
 * An alert rule's settings, for a create and for a full replace.
 *
 * @param minCalls        default 10
 * @param cooldownMinutes default 60
 * @param aiAgentId       null — every AI agent's calls
 * @param enabled         default true
 */
public record SaveAlertRuleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AlertMetric metric,
        @NotNull AlertComparator comparator,
        @NotNull Double threshold,
        @NotNull Integer windowMinutes,
        Integer minCalls,
        Integer cooldownMinutes,
        Long aiAgentId,
        Boolean enabled
) {
}
