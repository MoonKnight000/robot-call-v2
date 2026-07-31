package uz.murodjon.uysotvoice.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for the call report list ({@code POST /api/reports/calls/list}...).
 * {@code page}/{@code size} may be omitted (defaulted by {@link FilterInterface}), but a
 * value that is present and out of range is rejected rather than silently clamped.
 */
public record CallFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CallTableField, Sort.Direction> orders)
        implements FilterInterface<CallTableField> {

    /** Newest first by default, matching what a "what just happened" view needs. */
    public CallFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CallTableField.CALL_ID, Sort.Direction.DESC);
        }
    }
}
