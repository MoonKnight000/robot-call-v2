package uz.murodjon.robotcallv2.scenario.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.scenario.domain.enums.ScenarioTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + filter + sort for POST /api/scenarios/list (§10.7). */
public record ScenarioFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ScenarioTableField, Sort.Direction> orders,
        Boolean builtinOnly)
        implements FilterInterface<ScenarioTableField> {

    public ScenarioFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ScenarioTableField.NAME, Sort.Direction.ASC);
        }
    }
}
