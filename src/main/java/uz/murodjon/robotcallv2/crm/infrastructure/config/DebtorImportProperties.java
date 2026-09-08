package uz.murodjon.robotcallv2.crm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How much of Uysot to read when building a day's debtor list
 * ({@code voice-agent.crm.debtor-import.*}).
 *
 * <p>Every number here exists because the Open API allows 60 requests a minute and
 * resolving one debtor's phone costs two of them on top of the page that found the
 * contract. Left alone, a company with a thousand overdue contracts would spend most of an
 * hour inside one recurrence sweep.
 *
 * @param minDelayDays how many days a contract must be behind before its client is called.
 *                     {@code 1} is "any contract the CRM marks as late"; raise it to leave
 *                     clients who are a day out alone
 * @param maxDebtors   most debtors one sync will produce, and so the most calls the
 *                     campaign places that day
 * @param pageSize     contracts per {@code contract/filter} page (the endpoint's own
 *                     maximum is 100)
 * @param maxPages     how many pages are scanned before giving up on finding more overdue
 *                     contracts — a stop for the case where almost nothing is late
 */
@ConfigurationProperties(prefix = "voice-agent.crm.debtor-import")
public record DebtorImportProperties(
        int minDelayDays,
        int maxDebtors,
        int pageSize,
        int maxPages
) {

    public DebtorImportProperties {
        minDelayDays = minDelayDays > 0 ? minDelayDays : 1;
        maxDebtors = maxDebtors > 0 ? maxDebtors : 200;
        pageSize = pageSize > 0 && pageSize <= 100 ? pageSize : 100;
        maxPages = maxPages > 0 ? maxPages : 20;
    }
}
