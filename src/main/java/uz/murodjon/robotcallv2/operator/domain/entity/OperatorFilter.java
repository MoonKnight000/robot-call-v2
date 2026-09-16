package uz.murodjon.robotcallv2.operator.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + filter for POST /api/operators/list.
 *
 * @param status only operators currently in this status; null for all
 */
public record OperatorFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<OperatorTableField, Sort.Direction> orders,
        OperatorStatus status
) implements FilterInterface<OperatorTableField> {

    public OperatorFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(OperatorTableField.NAME, Sort.Direction.ASC);
        }
    }
}
