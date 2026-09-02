package uz.murodjon.robotcallv2.report.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.report.domain.enums.ReportScheduleTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** Pagination + sort for {@code POST /api/reports/schedule/list} (§10.10). */
public record ReportScheduleFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ReportScheduleTableField, Sort.Direction> orders)
        implements FilterInterface<ReportScheduleTableField> {

    public ReportScheduleFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ReportScheduleTableField.ID, Sort.Direction.ASC);
        }
    }
}

