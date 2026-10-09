package uz.murodjon.robotcallv2.tool.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolExecutionStatus;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolExecutionTableField;

import java.time.Instant;
import java.util.LinkedHashMap;

/**
 * The tool request log's page. Every criterion is optional; null means "any".
 *
 * @param from inclusive
 * @param to   exclusive
 */
public record ToolExecutionFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ToolExecutionTableField, Sort.Direction> orders,
        Long toolId,
        Long callAttemptId,
        ToolExecutionStatus status,
        Instant from,
        Instant to
) implements FilterInterface<ToolExecutionTableField> {

    public ToolExecutionFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ToolExecutionTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
