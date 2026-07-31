package uz.murodjon.uysotvoice.donotcall.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for {@code POST /api/do-not-call/list} (§10.8 DNC tab). */
public record DoNotCallFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<DoNotCallTableField, Sort.Direction> orders)
        implements FilterInterface<DoNotCallTableField> {

    /** Most recently added first. */
    public DoNotCallFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(DoNotCallTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
