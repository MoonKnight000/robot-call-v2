package uz.murodjon.robotcallv2.schedule.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.schedule.domain.enums.HolidayTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort for POST /api/holidays/list.
 *
 * @param year only that calendar year's holidays; null for all of them
 */
public record HolidayFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<HolidayTableField, Sort.Direction> orders,
        @Min(2000) @Max(2100) Integer year
) implements FilterInterface<HolidayTableField> {

    public HolidayFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(HolidayTableField.DATE, Sort.Direction.ASC);
        }
    }
}
