package uz.murodjon.uysotvoice.agent.dialog;

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

    /** What the engine should do about the current silence. */
    public enum Action {
        /** Line is not idle (or not idle long enough) — nothing to do. */
        NONE,
        /** Ask whether the caller is still there. */
        PROMPT,
        /** Prompts are exhausted; end the call. */
        END
    }

    private final Duration idleThreshold;
    private final int maxPrompts;

    private Instant lastActivity;
    private int prompts;
    private boolean finished;

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
        this.lastActivity = now;
    }

    /** Record activity on the line — anything that means the call is alive. */
    public synchronized void touch(Instant now) {
        lastActivity = now;
    }

    /**
     * Decide what the current silence calls for.
     *
     * @param botSpeaking  whether queued bot audio is still playing — the caller is
     *                     being spoken to, so the line is not idle
     * @param turnInFlight whether a turn is being processed; the caller's input has
     *                     already landed and an answer is on its way
     */
    public synchronized Action check(Instant now, boolean botSpeaking, boolean turnInFlight) {
        if (finished) {
            return Action.NONE;
        }
        if (botSpeaking || turnInFlight) {
            // Not silence — and the idle clock has to restart from here, or the call
            // would be judged idle the moment a long reply finishes playing.
            lastActivity = now;
            return Action.NONE;
        }
        if (Duration.between(lastActivity, now).compareTo(idleThreshold) < 0) {
            return Action.NONE;
        }
        if (prompts >= maxPrompts) {
            finished = true;
            return Action.END;
        }
        prompts++;
        lastActivity = now; // the prompt itself resets the clock
        return Action.PROMPT;
    }

    /** How many times the caller has been asked whether they are still there. */
    public synchronized int prompts() {
        return prompts;
    }
}
