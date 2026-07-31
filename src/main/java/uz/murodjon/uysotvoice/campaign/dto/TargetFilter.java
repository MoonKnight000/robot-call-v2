package uz.murodjon.uysotvoice.campaign.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for {@code POST /api/campaigns/{id}/targets/list}. {@code page}/
 * {@code size} may be omitted (defaulted by {@link FilterInterface}), but a value that is
 * present and out of range is rejected rather than silently clamped.
 */
public record TargetFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<TargetTableField, Sort.Direction> orders)
        implements FilterInterface<TargetTableField> {

    public TargetFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(TargetTableField.ID, Sort.Direction.ASC);
        }
    }
}
