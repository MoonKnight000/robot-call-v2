package uz.murodjon.robotcallv2.agent.turn;

/**
 * What a {@link TurnSignal} is asked about: the pause the caller is in now. Audio is not in it —
 * a signal that listens keeps its own buffer ({@link TurnSignal#audioListener}).
 *
 * @param interim   the recognizer's latest interim for the utterance; empty before the first one
 * @param agentLine the agent's last line — what the caller is answering; empty when there was none
 */
public record TurnMoment(String interim, String agentLine) {

    public TurnMoment {
        interim = interim == null ? "" : interim;
        agentLine = agentLine == null ? "" : agentLine;
    }
}
