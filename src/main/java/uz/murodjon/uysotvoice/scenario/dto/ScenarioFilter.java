package uz.murodjon.uysotvoice.scenario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for {@code POST /api/scenarios/list}. Only ever lists the active
 * version of each {@code scenario_key} — historical versions exist so a running
 * campaign keeps its behavior, not to be browsed as separate rows here.
 *
 * @param builtinOnly {@code true} for "Tayyor shablonlar" only, {@code false} for
 *                    "Mening ssenariylarim" only, {@code null} for both (§10.7)
 */
public record ScenarioFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ScenarioTableField, Sort.Direction> orders,
        Boolean builtinOnly)
        implements FilterInterface<ScenarioTableField> {

    public ScenarioFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ScenarioTableField.ID, Sort.Direction.ASC);
        }
    }
}
