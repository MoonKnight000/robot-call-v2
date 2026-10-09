package uz.murodjon.robotcallv2.alert.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertMetric;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertRuleTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/** The alert rules' page; {@code metric} and {@code enabled} null mean "any". */
public record AlertRuleFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<AlertRuleTableField, Sort.Direction> orders,
        AlertMetric metric,
        Boolean enabled
) implements FilterInterface<AlertRuleTableField> {

    public AlertRuleFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(AlertRuleTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
