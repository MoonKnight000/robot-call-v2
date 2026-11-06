package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question asked of the knowledge base from the playground, answered the way a call would answer it.
 *
 * @param agentId  search as this agent does — its own sources, the company-wide ones and its collections, with its
 *                 {@code ragTopK} and {@code ragMinScore}; null searches the whole company with the defaults
 * @param limit    overrides the agent's top-k for this search
 * @param minScore overrides the agent's similarity floor for this search
 * @param language picks the curated answer's translation; null is Uzbek
 */
public record KnowledgeSearchRequest(
        @NotBlank @Size(max = 500) String question,
        Long agentId,
        @Min(1) @Max(10) Integer limit,
        @DecimalMin("0.0") @DecimalMax("1.0") Double minScore,
        @Size(max = 10) String language
) {
}
