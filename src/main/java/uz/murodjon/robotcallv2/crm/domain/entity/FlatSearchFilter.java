package uz.murodjon.robotcallv2.crm.domain.entity;

import java.math.BigDecimal;

/**
 * What a caller asked for. Every field optional; only flats on sale are ever searched.
 *
 * @param limit how many flats to return, at most 20; 0 or less (left out of a request) means 5
 */
public record FlatSearchFilter(
        Integer rooms,
        BigDecimal priceFrom,
        BigDecimal priceTo,
        Long houseId,
        Integer floorFrom,
        Integer floorTo,
        int limit
) {
    public FlatSearchFilter {
        limit = limit <= 0 ? 5 : Math.min(limit, 20);
    }
}
