package uz.murodjon.robotcallv2.pack.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentDataPolicy;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataEvaluationCriterion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.util.List;

/**
 * The part of an agent a pack decides. Voice, engine, limits and the rest are left to the
 * agent's own defaults — they belong to the company, not to the industry.
 *
 * @param scenario the agent's own scenario, stored on the agent rather than as a shared
 *                 scenario row, so a pack can be installed more than once
 * @param useRag     whether the agent searches the company's knowledge base before answering.
 *                   An industry decision rather than a company one: a support line without it
 *                   answers from the prompt alone, and a survey call with it pays for a search
 *                   on every turn it has nothing to look up. Null leaves the agent's default (off)
 * @param dataPolicy what is kept after the call, when the industry decides that rather than the
 *                   company (UNIVERSAL-OPERATOR-PLAN E.3): a clinic's calls carry why somebody
 *                   is seeing a doctor, and that is not something to keep by default. All of it
 *                   or none of it — null leaves every part to the agent's own defaults, and a
 *                   pack that sets it is saying the industry answers all four questions
 */
public record PackAgent(
        String name,
        String description,
        String language,
        String firstMessage,
        Boolean useRag,
        AiAgentDataPolicy dataPolicy,
        ScenarioDefinition scenario,
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation
) {
}
