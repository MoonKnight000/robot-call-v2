package uz.murodjon.uysotvoice.apikey.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.apikey.enums.ApiKeyTableField;
import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for {@code POST /api/settings/api-keys/list}. */
public record ApiKeyFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ApiKeyTableField, Sort.Direction> orders)
        implements FilterInterface<ApiKeyTableField> {

    public ApiKeyFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ApiKeyTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
