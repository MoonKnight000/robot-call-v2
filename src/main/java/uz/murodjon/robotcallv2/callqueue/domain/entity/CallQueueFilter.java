package uz.murodjon.robotcallv2.callqueue.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for POST /api/call-queues/list. */
public record CallQueueFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CallQueueTableField, Sort.Direction> orders
) implements FilterInterface<CallQueueTableField> {

    public CallQueueFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallQueueTableField.NAME, Sort.Direction.ASC);
        }
    }
}
