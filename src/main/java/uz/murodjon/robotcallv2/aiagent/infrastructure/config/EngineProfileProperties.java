package uz.murodjon.robotcallv2.aiagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import uz.murodjon.robotcallv2.aiagent.domain.entity.EngineProfile;

import java.util.List;

/**
 * The engine profiles an agent form offers. Bound from {@code voice-agent.engine-profiles.*}.
 *
 * @param items profiles in the order the form lists them; one this build cannot run is left out
 *              of the listing, not reported as an error
 */
@ConfigurationProperties(prefix = "voice-agent.engine-profiles")
public record EngineProfileProperties(List<EngineProfile> items) {

    public EngineProfileProperties {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
