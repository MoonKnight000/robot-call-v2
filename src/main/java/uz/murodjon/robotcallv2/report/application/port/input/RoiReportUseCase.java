package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.report.application.dto.RoiReportResponse;

public interface RoiReportUseCase {

    /**
     * @param from                  ISO instant or YYYY-MM-DD in the company's zone; null = 7 days before {@code to}
     * @param to                    the same; null = now
     * @param campaignId            only this campaign's calls; null = every call
     * @param operatorHourlyCostUzs the company's own operator hour; null = the platform default
     */
    RoiReportResponse findRoiReport(long companyId, String from, String to, Long campaignId,
                                    Long operatorHourlyCostUzs);
}
