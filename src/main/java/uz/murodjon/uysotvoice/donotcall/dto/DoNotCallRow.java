package uz.murodjon.uysotvoice.donotcall.dto;

import java.time.Instant;

/** One row of {@code POST /api/do-not-call/list} (§10.8 DNC tab). */
public record DoNotCallRow(long id, String phone, String reason, String source, Instant createdAt) {
}
