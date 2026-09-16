package uz.murodjon.robotcallv2.insight.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.insight.domain.enums.AgentInsightTableField;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightStatus;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * The insight inbox's page.
 *
 * @param status null — every proposal whatever its standing; set — only those in that state
 */
public record AgentInsightFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<AgentInsightTableField, Sort.Direction> orders,
        Long aiAgentId,
        InsightType type,
        InsightStatus status
) implements FilterInterface<AgentInsightTableField> {

    public AgentInsightFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(AgentInsightTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
