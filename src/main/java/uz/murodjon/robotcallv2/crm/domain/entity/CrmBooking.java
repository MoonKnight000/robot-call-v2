package uz.murodjon.robotcallv2.crm.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One booking (reservation) of a flat, as the CRM states it.
 *
 * @param status       the CRM's own status label, e.g. ACTIVE, CANCELED, CONVERTED
 * @param bookedUntil  when the reservation lapses, or {@code null}
 * @param amount       the booking fee, or {@code null}
 */
public record CrmBooking(
        long id,
        String houseName,
        String flatNumber,
        String status,
        LocalDate bookedUntil,
        BigDecimal amount,
        String currency
) {
}
