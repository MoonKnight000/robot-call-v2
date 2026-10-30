package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.domain.entity.CallCostBreakdown;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;

public interface ModelPricingUseCase {

    /**
     * What {@code usage} costs at the current rates, line by line, with the chat model's own
     * token prices when it has them. Charges nothing — for the price a picker shows.
     *
     * @param llmModel the chat model the tokens ran on, or null for the common LLM rate
     */
    CallCostBreakdown findCostBreakdown(CallUsage usage, String llmModel);
}
