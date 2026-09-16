package uz.murodjon.robotcallv2.company.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The company that owns startup-time work with no request behind it — {@code
 * CompanyBootstrap}, {@code TtsWarmup}, {@code DialogSimulationRunner}. Bound from
 * {@code voice-agent.company.*}.
 *
 * <p>Only for those. A request's company always comes from its token
 * ({@code @CurrentCompanyId}) and travels as an argument through every layer, never as
 * hidden context — this is the one place a company id may be assumed, and it is assumed
 * out loud. Matches the bootstrap row {@code R__seed_data.sql} seeds as id 1.
 */
@ConfigurationProperties(prefix = "voice-agent.company")
public class CompanyProperties {

    private long defaultId = 1L;

    public long defaultId() {
        return defaultId;
    }

    public void setDefaultId(long defaultId) {
        this.defaultId = defaultId;
    }
}
