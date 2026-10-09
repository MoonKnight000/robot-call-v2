package uz.murodjon.robotcallv2.company.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param maxConcurrentCalls how many dialer calls the company may hold at once; null returns it to
 *                           the platform default
 */
public record UpdateCompanyConcurrencyLimitRequest(
        @Min(1) @Max(10_000) Integer maxConcurrentCalls
) {
}
