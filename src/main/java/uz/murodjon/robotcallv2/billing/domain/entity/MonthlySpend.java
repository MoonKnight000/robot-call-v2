package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * One month of a company's charged calls.
 *
 * @param period      {@code yyyy-MM} in the company's timezone
 * @param spendUzs    what was charged that month
 * @param durationSec connected seconds behind that charge
 */
public record MonthlySpend(String period, long spendUzs, long durationSec) {
}
