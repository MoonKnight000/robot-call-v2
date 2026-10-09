package uz.murodjon.robotcallv2.wrapup.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;
import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpCodeTableField;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for POST /api/wrap-up-codes/list.
 *
 * @param enabled true for the codes an operator may pick now, false for the disabled ones,
 *                null for both
 */
public record WrapUpCodeFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<WrapUpCodeTableField, Sort.Direction> orders,
        Boolean enabled
) implements FilterInterface<WrapUpCodeTableField> {

    public WrapUpCodeFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(WrapUpCodeTableField.NAME, Sort.Direction.ASC);
        }
    }
}
