package uz.murodjon.robotcallv2.quality.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.quality.domain.enums.ScorecardCriterionTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for POST /api/scorecard-criteria/list.
 *
 * @param enabled true for the criteria calls are marked on now, false for the disabled ones,
 *                null for both
 */
public record ScorecardCriterionFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ScorecardCriterionTableField, Sort.Direction> orders,
        Boolean enabled
) implements FilterInterface<ScorecardCriterionTableField> {

    public ScorecardCriterionFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ScorecardCriterionTableField.ID, Sort.Direction.ASC);
        }
    }
}
