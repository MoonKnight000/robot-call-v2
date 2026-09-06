package uz.murodjon.robotcallv2.audit.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.audit.domain.enums.AuditTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort + filter for POST /api/reports/audit/list.
 */
public record AuditFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<AuditTableField, Sort.Direction> orders,
        String actor,
        String action,
        String entity
) implements FilterInterface<AuditTableField> {

    public AuditFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(AuditTableField.ID, Sort.Direction.DESC);
        }
    }
}
