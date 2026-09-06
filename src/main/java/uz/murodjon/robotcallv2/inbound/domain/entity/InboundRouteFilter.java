package uz.murodjon.robotcallv2.inbound.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.inbound.domain.enums.InboundRouteTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + filter + sort for POST /api/inbound-routes/list. */
public record InboundRouteFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<InboundRouteTableField, Sort.Direction> orders)
        implements FilterInterface<InboundRouteTableField> {

    public InboundRouteFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(InboundRouteTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
