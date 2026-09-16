package uz.murodjon.robotcallv2.schedule.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.schedule.domain.enums.BusinessScheduleTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for POST /api/schedules/list. */
public record BusinessScheduleFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<BusinessScheduleTableField, Sort.Direction> orders
) implements FilterInterface<BusinessScheduleTableField> {

    public BusinessScheduleFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(BusinessScheduleTableField.NAME, Sort.Direction.ASC);
        }
    }
}
