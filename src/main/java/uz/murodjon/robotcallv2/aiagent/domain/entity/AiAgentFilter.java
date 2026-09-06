package uz.murodjon.robotcallv2.aiagent.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

public record AiAgentFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<AiAgentTableField, Sort.Direction> orders,
        String search,
        Long scenarioId,
        Boolean enabled)
        implements FilterInterface<AiAgentTableField> {

    public AiAgentFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(AiAgentTableField.CREATED_AT, Sort.Direction.DESC);
            orders.put(AiAgentTableField.ID, Sort.Direction.DESC);
        } else if (!orders.containsKey(AiAgentTableField.ID)) {
            LinkedHashMap<AiAgentTableField, Sort.Direction> normalized = new LinkedHashMap<>(orders);
            normalized.put(AiAgentTableField.ID, Sort.Direction.DESC);
            orders = normalized;
        }
    }
}
