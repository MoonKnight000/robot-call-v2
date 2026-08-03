package uz.murodjon.uysotvoice.inbound.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.inbound.enums.InboundRouteTableField;
import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for {@code POST /api/inbound-routes/list} (ROADMAP C.1). */
public record InboundRouteFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<InboundRouteTableField, Sort.Direction> orders)
        implements FilterInterface<InboundRouteTableField> {

    public InboundRouteFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(InboundRouteTableField.ID, Sort.Direction.ASC);
        }
    }
}
