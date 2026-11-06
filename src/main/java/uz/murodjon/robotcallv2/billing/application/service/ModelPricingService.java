package uz.murodjon.robotcallv2.billing.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.billing.application.port.input.ModelPricingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingLlmRates;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingRates;
import uz.murodjon.robotcallv2.billing.domain.entity.CallCostBreakdown;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelPrice;
import uz.murodjon.robotcallv2.billing.domain.service.CallCostCalculator;
import uz.murodjon.robotcallv2.billing.infrastructure.config.BillingModelRateProperties;
import uz.murodjon.robotcallv2.billing.infrastructure.config.BillingRateProperties;

/**
 * Prices a usage for the model picker. Settlement of real calls is {@code CallBillingService}'s
 * and still uses the common LLM rate; the per-model rates here only change the figure shown.
 */
@Service
public class ModelPricingService implements ModelPricingUseCase {

    private final BillingRateProperties billingRateProperties;
    private final BillingModelRateProperties billingModelRateProperties;
    private final ModelPriceService modelPriceService;

    public ModelPricingService(BillingRateProperties billingRateProperties,
                               BillingModelRateProperties billingModelRateProperties,
                               ModelPriceService modelPriceService) {
        this.billingRateProperties = billingRateProperties;
        this.billingModelRateProperties = billingModelRateProperties;
        this.modelPriceService = modelPriceService;
    }

    @Override
    public CallCostBreakdown findCostBreakdown(CallUsage usage, String llmModel) {
        BillingRates rates = billingRateProperties.rates();
        // The platform's price list first (/api/platform/model-prices), then billing.yml's model-rates.
        ModelPrice price = llmModel == null ? null : modelPriceService.findPriceLookup().apply(llmModel);
        BillingLlmRates modelRates = price != null ? price.llm()
                : llmModel == null ? null : billingModelRateProperties.llm().get(llmModel);
        if (modelRates != null) {
            rates = new BillingRates(modelRates, rates.sttPerMinuteUzs(), rates.ttsPer1kCharsUzs(),
                    rates.realtimePerMinuteUzs(), rates.telephonyPerMinuteUzs(), rates.platformFeePerMinuteUzs(),
                    rates.markupBasisPoints());
        }
        return CallCostCalculator.calculate(usage, rates);
    }
}
