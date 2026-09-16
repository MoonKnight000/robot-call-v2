package uz.murodjon.robotcallv2.inbound.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.inbound.domain.enums.CallerRuleTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for POST /api/inbound-caller-rules/list. */
public record InboundCallerRuleFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CallerRuleTableField, Sort.Direction> orders
) implements FilterInterface<CallerRuleTableField> {

    public InboundCallerRuleFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallerRuleTableField.PHONE_PREFIX, Sort.Direction.ASC);
        }
    }
}
