package uz.murodjon.robotcallv2.company.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
