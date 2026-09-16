package uz.murodjon.robotcallv2.missedcall.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

/**
 * Pagination + filter for POST /api/missed-calls/list.
 *
 * @param status         only this status; null for all
 * @param reason         only this reason; null for all
 * @param assignedUserId only calls assigned to this user; null for all
 */
public record MissedCallFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<MissedCallTableField, Sort.Direction> orders,
        MissedCallStatus status,
        MissedCallReason reason,
        Long assignedUserId
) implements FilterInterface<MissedCallTableField> {

    public MissedCallFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(MissedCallTableField.LAST_AT, Sort.Direction.DESC);
        }
    }
}
