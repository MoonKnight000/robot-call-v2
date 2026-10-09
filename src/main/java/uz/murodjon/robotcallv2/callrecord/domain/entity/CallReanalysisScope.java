package uz.murodjon.robotcallv2.callrecord.domain.entity;

/**
 * The calls a reanalysis would go through, before it is queued.
 *
 * @param transcriptChars the length of all their transcripts together — what the estimate is priced on
 */
public record CallReanalysisScope(long callCount, long transcriptChars) {
}
