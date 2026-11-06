package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.application.dto.CostReportResponse;

import java.time.LocalDate;

/** What a company spent, line by line, over a period of days. */
public interface BillingCostUseCase {

    /**
     * @param from first day, inclusive; null = the first day of the current month
     * @param to   last day, inclusive; null = today
     */
    CostReportResponse findCostReport(long companyId, LocalDate from, LocalDate to);
}
