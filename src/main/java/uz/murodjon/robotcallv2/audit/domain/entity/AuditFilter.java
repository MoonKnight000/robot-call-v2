package uz.murodjon.robotcallv2.audit.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.audit.domain.enums.AuditTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.time.Instant;
import java.util.LinkedHashMap;

/**
 * Pagination + sort + filter for POST /api/reports/audit/list.
 *
 * @param actionPrefix a group of actions by their prefix ("AI_AGENT_", "CALL_", "USER_")
 * @param q            text searched in the detail, the entity id and the actor, case-insensitive
 * @param dateFrom     entries written at or after this instant
 * @param dateTo       entries written before this instant
 */
public record AuditFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<AuditTableField, Sort.Direction> orders,
        String actor,
        String action,
        String entity,
        String actionPrefix,
        String entityId,
        String q,
        Instant dateFrom,
        Instant dateTo
) implements FilterInterface<AuditTableField> {

    public AuditFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(AuditTableField.ID, Sort.Direction.DESC);
        }
    }
}
