package uz.murodjon.robotcallv2.agent.audio;

/**
 * Told how one of the {@link SpeechGate}'s closes turned out, once the reopen grace has
 * settled it: either the caller spoke again inside the grace (the close cut a pause, not a
 * turn) or the grace passed and the close stood.
 *
 * <p>Called on the thread feeding the gate — the RTP consumer — so it must not block.
 */
@FunctionalInterface
public interface GateCloseListener {

    /**
     * @param transcript      the recognizer's last interim when the gate closed; empty when
     *                        none had arrived
     * @param waitMs          the silence the close waited out
     * @param callerContinued whether the caller carried on within the reopen grace
     * @param agentLine       the agent's last line before the utterance — what the caller was
     *                        answering; empty when the agent had not spoken yet
     * @param decision        what ended the utterance and what every turn signal said then —
     *                        the LLM's verdict ({@link SpeechGate#setLlmTurnCue}) among them
     */
    void closeJudged(String transcript, int waitMs, boolean callerContinued, String agentLine, TurnCloseDecision decision);
}
