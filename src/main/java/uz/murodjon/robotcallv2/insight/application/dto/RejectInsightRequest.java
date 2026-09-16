package uz.murodjon.robotcallv2.insight.application.dto;

import jakarta.validation.constraints.Size;

/**
 * @param reason why the proposal was turned down; kept so the loop can be argued with later
 */
public record RejectInsightRequest(@Size(max = 500) String reason) {
}
