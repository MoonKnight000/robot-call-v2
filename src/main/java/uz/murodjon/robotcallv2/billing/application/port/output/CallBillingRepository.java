package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** What each call was charged, and the sums the billing screens are built from. */
public interface CallBillingRepository {

    CallBilling save(CallBilling callBilling);

    Optional<CallBilling> findByCallAttemptId(long callAttemptId);

    /**
     * Adds the post-call summary to a settled call's row: its tokens to their own summary
     * columns, its price to the LLM line and the total.
     */
    void addSummaryCharge(long callAttemptId, CallUsage summaryUsage, long costUzs);

    /** The oldest hold still open for that target, if one was taken. */
    Optional<CallBilling> findOpenHold(long companyId, long targetId);

    /** Newest month first, at most {@code limit} months. */
    List<MonthlySpend> findMonthlySpend(long companyId, int limit);

    /** Everything the company was charged for since {@code from}. */
    PeriodUsage findUsageSince(long companyId, Instant from);

    /**
     * What each A/B variant of one campaign has been charged, plus a row with a null
     * variant for the calls that ran none.
     */
    List<VariantSpend> findSpendByVariant(long companyId, long campaignId);

    /** What the campaign's settled calls were charged since {@code since} (all time when null), in UZS. */
    long sumSpentUzsByCampaign(long companyId, long campaignId, Instant since);
}
