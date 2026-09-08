package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * What is kept after the call ends, and for how long.
 *
 * <p>One group because they are answered together, by whoever signs off on the company's
 * data handling rather than by whoever tunes the voice: turning on {@code zeroPiiRetention}
 * while leaving the audio stored keeps the very thing the setting was meant to remove.
 *
 * @param zeroPiiRetention        strip personal data from what is stored about the call
 * @param storeCallAudio          keep the recording at all
 * @param conversationRetentionDays how long the transcript is kept; never null
 */
public record AiAgentDataPolicy(
        boolean zeroPiiRetention,
        boolean storeCallAudio,
        Integer conversationRetentionDays
) {
    /** A month: long enough to settle a dispute about a call, short enough not to be an archive. */
    private static final int DEFAULT_RETENTION_DAYS = 30;

    public AiAgentDataPolicy {
        if (conversationRetentionDays == null) {
            conversationRetentionDays = DEFAULT_RETENTION_DAYS;
        }
    }
}
