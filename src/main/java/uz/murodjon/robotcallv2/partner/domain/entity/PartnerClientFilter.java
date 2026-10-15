package uz.murodjon.robotcallv2.partner.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.partner.domain.enums.PartnerClientTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for POST /api/partner/clients/list. */
public record PartnerClientFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<PartnerClientTableField, Sort.Direction> orders)
        implements FilterInterface<PartnerClientTableField> {

    public PartnerClientFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(PartnerClientTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
