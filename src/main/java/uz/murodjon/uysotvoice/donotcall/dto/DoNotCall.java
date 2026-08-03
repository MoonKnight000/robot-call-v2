package uz.murodjon.uysotvoice.donotcall.dto;

import uz.murodjon.uysotvoice.donotcall.enums.DoNotCallSource;

import java.time.Instant;

/** One row of {@code POST /api/do-not-call/list} (§10.8 DNC tab). */
public record DoNotCall(long id, String phone, String reason, DoNotCallSource source, Instant createdAt) {
}
