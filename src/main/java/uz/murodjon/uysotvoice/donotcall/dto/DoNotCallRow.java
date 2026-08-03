package uz.murodjon.uysotvoice.donotcall.dto;

import uz.murodjon.uysotvoice.donotcall.enums.DoNotCallSource;

import java.time.Instant;

/**
 * {@link DoNotCall} enriched with the contact's name for the API response
 * (backend-uchun-talablar.md §15) — a projection over {@code do_not_call_list} and
 * {@code contact} (matched by phone, the same precedent {@code ContactService} already
 * uses for the call-history timeline), so it takes the {@code <Noun>Row} suffix rather
 * than bare {@code DoNotCall}.
 *
 * @param contactName resolved by phone from {@code contact}; {@code null} if no contact
 *                    with this phone exists (an opt-out can be recorded for a number
 *                    that was never added as a contact)
 */
public record DoNotCallRow(long id, String phone, String contactName, String reason, DoNotCallSource source,
                            Instant createdAt) {

    public static DoNotCallRow of(DoNotCall d, String contactName) {
        return new DoNotCallRow(d.id(), d.phone(), contactName, d.reason(), d.source(), d.createdAt());
    }
}
