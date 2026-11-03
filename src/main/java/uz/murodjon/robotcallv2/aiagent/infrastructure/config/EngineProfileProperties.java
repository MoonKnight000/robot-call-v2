package uz.murodjon.robotcallv2.aiagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import uz.murodjon.robotcallv2.aiagent.domain.entity.EngineProfile;

import java.util.List;

/**
 * The engine profiles an agent form offers. Bound from {@code voice-agent.engine-profiles.*}.
 *
 * <p>One-click engine bundles (speed / cost / quality). An agent keeps whatever the request
 * names itself; a profile only fills the rest. The voice is not part of it: it decides the
 * synthesizer. {@code GET /api/ai-agents/engine-profiles} lists only the profiles this build
 * can run in full. The same id is listed once per chat vendor; the first vendor (in this
 * order) with a key in this deployment supplies it.
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
