package uz.murodjon.robotcallv2.callrecord.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisMode;

import java.time.Instant;

/**
 * Which calls to analyse again: the agent's calls started in [{@code from}, {@code to}).
 * The same body previews the job and queues it.
 *
 * @param mode what is done with each call; {@code null} — {@code ANALYSIS}. The contact modes
 *             are the contacts page's "Backfill"
 */
public record CreateCallReanalysisRequest(
        @NotNull Long aiAgentId,
        @NotNull Instant from,
        @NotNull Instant to,
        CallReanalysisMode mode
) {

    public CreateCallReanalysisRequest {
        mode = mode == null ? CallReanalysisMode.ANALYSIS : mode;
    }
}
