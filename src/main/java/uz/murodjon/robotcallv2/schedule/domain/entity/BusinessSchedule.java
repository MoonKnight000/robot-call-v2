package uz.murodjon.robotcallv2.schedule.domain.entity;

import java.time.Instant;
import java.util.List;

/**
 * A company's weekly opening hours, shared by every inbound route, call flow and queue
 * that points at it (docs/INBOUND-PBX-PLAN.md §5).
 *
 * @param timezone IANA zone the slots are in; null means the company's own
 *                 ({@code company_config.timezone})
 */
public record BusinessSchedule(
        long id,
        long companyId,
        String name,
        String timezone,
        List<BusinessScheduleSlot> slots,
        Instant createdAt
) {
    public BusinessSchedule {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /** What a create or update writes; id, company and creation time belong to storage. */
    public static BusinessSchedule draft(String name, String timezone, List<BusinessScheduleSlot> slots) {
        return new BusinessSchedule(0, 0, name, timezone, slots, null);
    }
}
