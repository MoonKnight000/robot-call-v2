package uz.murodjon.robotcallv2.billing.domain.entity;

import java.time.LocalDate;

/**
 * What was spent on one Tashkent calendar day.
 *
 * @param day the day
 * @param uzs what it cost
 */
public record DailySpend(LocalDate day, long uzs) {
}
