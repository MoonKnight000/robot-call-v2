package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.Objects;

/**
 * A named bundle of the engine choices that trade speed, cost and quality against each other:
 * what listens, which models think, how long they may talk. Picking one fills those fields on
 * a new agent in one step.
 *
 * <p>The voice is not part of it: a voice belongs to one synthesizer, so the voice an agent
 * speaks with already decides which one runs. A null field leaves the deployment's own choice.
 *
 * @param id              stable id the create request names
 * @param label           name shown in the form
 * @param description     one line on what the profile is for
 * @param sttProvider     recognizer, or null for the deployment's
 * @param sttModel        recognizer model, only with its provider
 * @param llmModel        model behind each turn (CASCADE catalog)
 * @param fastLlmModel    cheaper model for barge-in decisions and short answers
 * @param temperature     sampling temperature
 * @param maxOutputTokens cap on one reply
 */
public record EngineProfile(
        String id,
        String label,
        String description,
        String sttProvider,
        String sttModel,
        String llmModel,
        String fastLlmModel,
        Double temperature,
        Integer maxOutputTokens
) {

    /** What a create without a profile starts from: every field left to the request and the defaults. */
    public static final EngineProfile NONE = new EngineProfile(null, null, null, null, null, null, null, null, null);

    /** Whether the agent's engine choices are exactly this profile's — the profile a form shows as picked. */
    public boolean isAppliedTo(AiAgent agent) {
        return Objects.equals(sttProvider, agent.speechEngine().sttProvider())
                && Objects.equals(sttModel, agent.speechEngine().sttModel())
                && Objects.equals(llmModel, agent.llmModel())
                && Objects.equals(fastLlmModel, agent.fastLlmModel())
                && Objects.equals(temperature, agent.temperature())
                && Objects.equals(maxOutputTokens, agent.maxOutputTokens());
    }
}
