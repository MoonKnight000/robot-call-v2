package uz.murodjon.robotcallv2.callrecord.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * When a call's conversation — its transcript and its recording — has been kept long
 * enough, as of one instant.
 *
 * <p>The rule a call falls under is its agent's: the agent the call ran, else its campaign's
 * agent, else its inbound route's agent.
 * <ul>
 *   <li>a {@code zeroPiiRetention} agent — gone once the call ended before
 *       {@code zeroPiiCutoff}. A call that never closed counts as ended a day after it
 *       started, so a live call (an operator transfer can run long) keeps its transcript;</li>
 *   <li>any other agent — gone after its own {@code conversationRetentionDays}, counted back
 *       from {@code now}; 0 or less keeps it forever;</li>
 *   <li>no agent — gone when older than {@code defaultCutoff}.</li>
 * </ul>
 * A call's age is when it ended, or when it started if it never closed, or when its row was
 * written if it never started — so a call whose end was never recorded is purged too.
 *
 * @param now           what an agent's days are counted back from
 * @param defaultCutoff calls no agent ran, older than this, lose their conversation;
 *                      {@link #KEEP_FOREVER} when the deployment keeps them
 * @param zeroPiiCutoff a zero-PII agent's call that ended before this loses its conversation
 */
public record ConversationRetention(Instant now, Instant defaultCutoff, Instant zeroPiiCutoff) {

    /** A cutoff no call is older than: the conversation is kept. */
    public static final Instant KEEP_FOREVER = Instant.EPOCH;

    /**
     * @param defaultDays  how long a call no agent ran keeps its conversation; 0 or less keeps it
     * @param zeroPiiGrace how long a zero-PII agent's conversation may outlive the call
     */
    public static ConversationRetention of(Instant now, int defaultDays, Duration zeroPiiGrace) {
        Instant defaultCutoff = defaultDays > 0 ? now.minus(defaultDays, ChronoUnit.DAYS) : KEEP_FOREVER;
        return new ConversationRetention(now, defaultCutoff, now.minus(zeroPiiGrace));
    }
}
