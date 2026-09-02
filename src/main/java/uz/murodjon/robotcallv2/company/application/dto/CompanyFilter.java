package uz.murodjon.robotcallv2.company.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + filter + sort for POST /api/companies/list. */
public record CompanyFilter(
        String search,
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CompanyTableField, Sort.Direction> orders)
        implements FilterInterface<CompanyTableField> {

    public CompanyFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CompanyTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
