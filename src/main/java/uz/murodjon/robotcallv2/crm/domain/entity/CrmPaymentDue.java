package uz.murodjon.robotcallv2.crm.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of a contract's active monthly payment schedule.
 *
 * @param remaining what is still unpaid of this row
 */
public record CrmPaymentDue(LocalDate date, BigDecimal amount, BigDecimal remaining, boolean paid) {
}
