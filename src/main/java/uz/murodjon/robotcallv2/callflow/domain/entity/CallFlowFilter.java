package uz.murodjon.robotcallv2.callflow.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for POST /api/call-flows/list. */
public record CallFlowFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CallFlowTableField, Sort.Direction> orders
) implements FilterInterface<CallFlowTableField> {

    public CallFlowFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallFlowTableField.UPDATED_AT, Sort.Direction.DESC);
        }
    }
}
