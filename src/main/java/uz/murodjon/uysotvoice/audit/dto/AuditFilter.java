package uz.murodjon.uysotvoice.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.uysotvoice.audit.enums.AuditTableField;
import uz.murodjon.uysotvoice.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + sort + filter for {@code POST /api/reports/audit/list}. {@code page}/
 * {@code size} may be omitted (defaulted by {@link FilterInterface}), but a value that is
 * present and out of range is rejected rather than silently clamped.
 *
 * @param actor  restrict to one actor (exact match — actor strings include the role suffix,
 *               e.g. {@code admin-key:ADMIN}, so this is deliberately not a LIKE search)
 * @param action restrict to one action code, e.g. {@code CAMPAIGN_START}
 * @param entity restrict to one entity type, e.g. {@code campaign}
 */
public record AuditFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<AuditTableField, Sort.Direction> orders,
        String actor,
        String action,
        String entity
) implements FilterInterface<AuditTableField> {

    /**
     * Most recent entries first by default — what an incident review reads.
     */
    public AuditFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(AuditTableField.ID, Sort.Direction.DESC);
        }
    }
}
