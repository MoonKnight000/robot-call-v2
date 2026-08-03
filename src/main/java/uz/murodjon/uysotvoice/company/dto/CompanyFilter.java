package uz.murodjon.uysotvoice.company.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.company.enums.CompanyTableField;
import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for {@code POST /api/companies/list} (ROADMAP B.1).
 *
 * @param search free-text match against name, or null/blank for no filter
 */
public record CompanyFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CompanyTableField, Sort.Direction> orders,
        String search)
        implements FilterInterface<CompanyTableField> {

    public CompanyFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CompanyTableField.NAME, Sort.Direction.ASC);
        }
    }
}
