package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Matching the bot's pace to the caller's (human-like call plan, phase 4). Bound from
 * {@code voice-agent.dialog.caller-pace.*}.
 *
 * @param enabled                 off until the reference below is set from real calls
 *                                ({@code voice.stt.caller.words.per.second} p50)
 * @param referenceWordsPerSecond an ordinary caller's pace; a caller at it leaves the bot's speed alone
 * @param maxAdjustPercent        the most the bot's speed moves either way, in percent of its own
 * @param hurriedRatio            a caller this many times the reference is in a hurry: the reply is
 *                                kept to one short sentence
 */
@ConfigurationProperties(prefix = "voice-agent.dialog.caller-pace")
public record CallerPaceProperties(
        boolean enabled,
        double referenceWordsPerSecond,
        int maxAdjustPercent,
        double hurriedRatio
) {
}
