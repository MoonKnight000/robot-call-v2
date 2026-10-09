package uz.murodjon.robotcallv2.callrecord.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Which calls to analyse again: the agent's calls started in [{@code from}, {@code to}).
 * The same body previews the job and queues it.
 */
public record CreateCallReanalysisRequest(
        @NotNull Long aiAgentId,
        @NotNull Instant from,
        @NotNull Instant to
) {
}
