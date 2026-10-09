package uz.murodjon.robotcallv2.callrecord.application.dto;

/**
 * What a reanalysis would go through and roughly cost, before it is queued. The token counts
 * are estimated from the transcripts' length; the job is charged what the model really used.
 *
 * @param maxCalls the most one job may go through; a larger {@code callCount} is refused
 */
public record CallReanalysisPreviewResponse(
        long callCount,
        int maxCalls,
        long estimatedPromptTokens,
        long estimatedCompletionTokens,
        long estimatedCostUzs
) {
}
