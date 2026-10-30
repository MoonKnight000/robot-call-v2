package uz.murodjon.robotcallv2.billing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingLlmRates;

import java.util.Map;

/**
 * Token prices of individual chat models ({@code voice-agent.billing.model-rates.*}), for the
 * price the model picker shows. A model with no entry is priced at {@code rates.llm}.
 *
 * <p>Keys are model ids; an id with a dot has to be written in brackets in YAML
 * ({@code "[gemini-3.8-flash]"}) or Spring Boot drops the dot from the key.
 *
 * @param llm token prices by model id; null or empty means every model uses the common rate
 */
@ConfigurationProperties(prefix = "voice-agent.billing.model-rates")
public record BillingModelRateProperties(Map<String, BillingLlmRates> llm) {

    public BillingModelRateProperties {
        llm = llm == null ? Map.of() : Map.copyOf(llm);
    }
}
