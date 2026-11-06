package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.murodjon.robotcallv2.billing.domain.enums.CostComponent;

/**
 * One cost line of {@link CostReportResponse}.
 *
 * @param key   which line
 * @param uzs   what it cost
 * @param count how many charges, for the lines charged per use (chat, copilot, questions,
 *              re-analysis); absent for the call lines
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CostComponentDto(CostComponent key, long uzs, Long count) {
}
