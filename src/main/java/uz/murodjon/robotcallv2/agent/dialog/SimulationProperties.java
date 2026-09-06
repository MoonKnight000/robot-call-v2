package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Offline dialog-simulation settings. Bound from {@code voice-agent.simulation.*}.
 *
 * @param personasPath file of caller personas to replay; blank disables the runner
 * @param scenarioId   scenario the simulated calls run, 0 for the campaign default
 * @param language     BCP-47 language the simulated caller speaks
 * @param maxTurns     how many turns one simulated call may take before it is cut off
 * @param personaModel chat model that plays the caller side
 */
@ConfigurationProperties(prefix = "voice-agent.simulation")
public record SimulationProperties(
        String personasPath,
        long scenarioId,
        String language,
        int maxTurns,
        String personaModel
) {

    public SimulationProperties {
        if (language == null || language.isBlank()) {
            language = "uz-UZ";
        }
        if (maxTurns <= 0) {
            maxTurns = 12;
        }
        if (personaModel == null || personaModel.isBlank()) {
            personaModel = "gemini-3.8-flash";
        }
    }
}
