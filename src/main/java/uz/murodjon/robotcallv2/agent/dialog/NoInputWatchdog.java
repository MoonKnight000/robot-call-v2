package uz.murodjon.robotcallv2.agent.dialog;

import java.time.Duration;
import java.time.Instant;

/**
 * Watches one call for a line that has gone silent, and decides when to say something.
 *
 * <p>The dialog engine is purely reactive: a turn only happens when the recognizer emits
 * a final transcript. Nothing in that design notices the case where <em>no</em> final is
 * ever coming — the caller set the phone down, the recognizer dropped the utterance, or
 * a barge-in silenced the bot and the caller then said nothing. The call then sits mute
 * until the duration cap fires minutes later, which the caller experiences as a dead
 * line and which bills STT the whole time.
 *
 * <p>Deliberately a plain decision function rather than a timer: it holds no thread and
 * no session, so the same logic can be unit-tested against a fabricated clock. The
 * engine polls it and performs the action.
 *
 * <p>Thread-safe: the engine touches it from the STT and VAD threads and polls it from
 * a scheduler thread.
 */
public class NoInputWatchdog {

    private final Duration idleThreshold;
    private final int maxPrompts;

    private Instant lastActivity;
    /**
     * The last sign of life that can only be the caller or the bot — a transcript, a key, the bot
     * speaking, a prompt. What {@link #touchVoice} measures its allowance from.
     */
    private Instant lastConfirmedActivity;
    /** The idle threshold of the last {@link #check}, which may be a stage's own. */
    private Duration lastIdleThreshold;
    /**
     * How long the caller has kept the agent waiting since their last sign of life, not counting
     * the stretch since {@link #waitingSince}. Time the agent spends speaking or composing a reply
     * is not the caller's silence; unlike {@link #lastActivity}, a prompt does not reset it either.
     */
    private Duration callerSilence = Duration.ZERO;
    /** Since when the agent has been waiting on the caller; null while it is speaking or composing a reply. */
    private Instant waitingSince;
    private int prompts;
    private boolean finished;
    /** The limit of the last {@link #check}, which may be a stage's own; read by {@link #isLastPrompt()}. */
    private int lastMaxPrompts;

    /**
     * @param idleThreshold quiet time that counts as no input. Long enough to let a
     *                      caller think — a bot that talks over someone drawing breath
     *                      is worse than one that waits a beat too long
     * @param maxPrompts    how many times to ask before giving up on the call
     * @param now           start of the idle clock (the call's first activity)
     */
    public NoInputWatchdog(Duration idleThreshold, int maxPrompts, Instant now) {
        this.idleThreshold = idleThreshold;
        this.maxPrompts = Math.max(0, maxPrompts);
        this.lastMaxPrompts = this.maxPrompts;
        this.lastActivity = now;
        this.lastConfirmedActivity = now;
        this.lastIdleThreshold = idleThreshold;
        this.waitingSince = now;
    }

    /** Record activity on the line — anything that means the call is alive. */
    public synchronized void touch(Instant now) {
        lastConfirmedActivity = now;
        markAlive(now);
    }

    /**
     * Record voice the VAD heard that nothing has transcribed — maybe a caller whose words the
     * recognizer missed, maybe breathing or a noisy room.
     *
     * <p>It keeps the line alive for one idle threshold past the last confirmed activity, and no
     * longer. Counted in full, a VAD that scored breathing as speech held the prompt off for the
     * rest of the call: on a recorded call the caller sat silent for 50 s and was never asked
     * "Alo, eshityapsizmi?". A real caller the recognizer cannot hear is asked after two
     * thresholds instead of one.
     *
     * @return whether it counted
     */
    public synchronized boolean touchVoice(Instant now) {
        if (now.isAfter(lastConfirmedActivity.plus(lastIdleThreshold))) {
            return false;
        }
        markAlive(now);
        return true;
    }

    private void markAlive(Instant now) {
        lastActivity = now;
        callerSilence = Duration.ZERO;
        waitingSince = now;
    }

    /**
     * Decide what the current silence calls for.
     *
     * @param botSpeaking  whether queued bot audio is still playing — the caller is
     *                     being spoken to, so the line is not idle
     * @param turnInFlight whether a turn is being processed; the caller's input has
     *                     already landed and an answer is on its way
     */
    public synchronized NoInputAction check(Instant now, boolean botSpeaking, boolean turnInFlight) {
        return check(now, botSpeaking, turnInFlight, idleThreshold, maxPrompts);
    }

    /**
     * {@link #check(Instant, boolean, boolean)} against limits the caller passes in — the ones
     * the call's current stage sets ({@code StageOverrides.reminderSeconds/reminderMaxCount}).
     * The prompt count stays the call's: a stage that allows fewer than were already spent
     * ends the call at its next silence.
     */
    public synchronized NoInputAction check(Instant now, boolean botSpeaking, boolean turnInFlight,
                                            Duration idleThreshold, int maxPrompts) {
        lastMaxPrompts = Math.max(0, maxPrompts);
        lastIdleThreshold = idleThreshold;
        trackCallerSilence(now, botSpeaking || turnInFlight);
        if (finished) {
            return NoInputAction.NONE;
        }
        if (botSpeaking || turnInFlight) {
            // Not silence — and the idle clock has to restart from here, or the call
            // would be judged idle the moment a long reply finishes playing.
            lastActivity = now;
            lastConfirmedActivity = now;
            return NoInputAction.NONE;
        }
        if (Duration.between(lastActivity, now).compareTo(idleThreshold) < 0) {
            return NoInputAction.NONE;
        }
        if (prompts >= lastMaxPrompts) {
            finished = true;
            return NoInputAction.END;
        }
        prompts++;
        lastActivity = now; // the prompt itself resets the clock
        lastConfirmedActivity = now;
        return NoInputAction.PROMPT;
    }

    /**
     * Whether the caller has kept the agent waiting for at least {@code limit} in total — the
     * agent's "end call on silence" ceiling. Only time spent waiting on the caller counts: the
     * clock stands still while the bot speaks or a turn is in flight (a slow greeting is not the
     * caller's silence), and reminder prompts do not reset it.
     *
     * @param botSpeaking  whether queued bot audio is still playing
     * @param turnInFlight whether a reply is being composed
     */
    public synchronized boolean isCallerSilentFor(Instant now, boolean botSpeaking, boolean turnInFlight,
                                                  Duration limit) {
        trackCallerSilence(now, botSpeaking || turnInFlight);
        Duration silent = waitingSince == null ? callerSilence : callerSilence.plus(Duration.between(waitingSince, now));
        return !finished && silent.compareTo(limit) >= 0;
    }

    /** Stops the caller-silence clock while the agent is busy and restarts it once the agent waits again. */
    private void trackCallerSilence(Instant now, boolean agentBusy) {
        if (agentBusy && waitingSince != null) {
            callerSilence = callerSilence.plus(Duration.between(waitingSince, now));
            waitingSince = null;
        } else if (!agentBusy && waitingSince == null) {
            waitingSince = now;
        }
    }

    /** When the line was last heard alive — moves whenever the caller (or the VAD) is heard. */
    public synchronized Instant lastActivity() {
        return lastActivity;
    }

    /** How many prompts the last check allowed — the stage's own limit when it sets one. */
    public synchronized int maxPrompts() {
        return lastMaxPrompts;
    }

    /** How many times the caller has been asked whether they are still there. */
    public synchronized int prompts() {
        return prompts;
    }

    /**
     * Whether the prompt just issued is the last one before the call is ended. That prompt
     * says so — a caller who hears a second identical "alo?" has no reason to think the
     * next silence costs them the call. With a single prompt there is nothing to escalate
     * from, so it stays the plain question.
     */
    public synchronized boolean isLastPrompt() {
        return lastMaxPrompts > 1 && prompts == lastMaxPrompts;
    }
}
