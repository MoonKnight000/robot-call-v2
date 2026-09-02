package uz.murodjon.robotcallv2.donotcall.application.dto;

import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;

import java.time.Instant;

/** Row in {@code POST /api/do-not-call/list} response (§10.8). */
public record DoNotCallRow(
        long id,
        String phone,
        String contactName,
        String reason,
        DoNotCallSource source,
        Instant createdAt
) {
    public static DoNotCallRow of(DoNotCall d, String contactName) {
        return new DoNotCallRow(d.getId(), d.getPhone(), contactName, d.getReason(), d.getSource(), d.getCreatedAt());
    }
}
