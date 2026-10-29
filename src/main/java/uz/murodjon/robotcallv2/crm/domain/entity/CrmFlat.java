package uz.murodjon.robotcallv2.crm.domain.entity;

import java.math.BigDecimal;

/**
 * One flat as the CRM lists it — every figure exactly as the CRM states it, never computed here.
 *
 * @param price       price without repair ({@code null} when the CRM has none)
 * @param priceRepaired price with repair, or {@code null}
 * @param status      SALE, BOOKED, SOLD, RESERVE, DELIVERED or REGISTERED
 */
public record CrmFlat(
        long id,
        String houseName,
        String buildingName,
        String number,
        Integer rooms,
        BigDecimal area,
        Integer floor,
        BigDecimal price,
        BigDecimal priceRepaired,
        BigDecimal pricePerArea,
        String currency,
        String status
) {
}
