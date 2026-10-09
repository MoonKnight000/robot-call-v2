package uz.murodjon.robotcallv2.agent.turn;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Collection of end-of-turn training pairs from live calls ({@link TurnDatasetRecorder}).
 * Bound from {@code voice-agent.turn-dataset.*}.
 *
 * <p>Off by default: what the caller said is personal data, and this writes it to disk
 * (redacted, but still words). It is switched on for the weeks a dataset is being
 * gathered, then off again.
 *
 * @param enabled   master switch
 * @param directory where the daily {@code turn-dataset-YYYY-MM-DD.jsonl} files go
 */
@ConfigurationProperties(prefix = "voice-agent.turn-dataset")
public record TurnDatasetProperties(boolean enabled, String directory) {
}
