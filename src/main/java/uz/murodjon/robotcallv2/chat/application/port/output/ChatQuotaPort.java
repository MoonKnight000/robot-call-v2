package uz.murodjon.robotcallv2.chat.application.port.output;

/**
 * The chat counts every application instance shares.
 *
 * <p>A chat is opened from a page on the open internet, the same as a widget call, and every
 * message it holds is a model call somebody pays for. The agent's day is therefore counted in
 * one place rather than per instance, and counted atomically, so a burst cannot all read the
 * same free line before any of it is written down.
 */
public interface ChatQuotaPort {

    /**
     * Counts one more conversation against the agent's day, if the day has room for it.
     *
     * @return false when the day is spent, or when the shared counter cannot be reached —
     *         an open endpoint with a ceiling that switches itself off during an outage has
     *         no ceiling. Never throws
     */
    boolean bookSession(long aiAgentId, int dailyLimit);
}
