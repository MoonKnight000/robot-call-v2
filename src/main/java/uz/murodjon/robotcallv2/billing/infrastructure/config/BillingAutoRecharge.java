package uz.murodjon.robotcallv2.billing.infrastructure.config;

/**
 * When a company that asked to be topped up automatically gets topped up.
 *
 * <p>The threshold is deliberately not per company: a company chooses whether to be
 * recharged ({@code company_billing.auto_recharge}), the platform chooses at what balance
 * and by how much. Per-company amounts are a setting to add when somebody asks for it.
 *
 * @param thresholdUzs balance below which a top-up is raised
 * @param amountUzs    how much the top-up is for
 * @param cron         how often balances are checked
 */
public record BillingAutoRecharge(
        long thresholdUzs,
        long amountUzs,
        String cron
) {
}
