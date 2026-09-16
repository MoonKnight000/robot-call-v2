package uz.murodjon.robotcallv2.agent.dialog;

import java.util.List;

/**
 * One agent turn with no audio in it: what the agent said, and what the fact guard thought
 * of it (UNIVERSAL-OPERATOR-PLAN E.4).
 *
 * @param line       the agent's reply; null or blank when the model produced nothing
 * @param violations figures in {@code line} that the call's facts and tool answers do not
 *                   support. On a voice call this is what stops a sentence before it is
 *                   spoken; with no audio there is nothing to stop, so the caller decides —
 *                   an evaluation fails the case, a chat logs it against the session
 */
public record TextTurn(String line, List<String> violations) {

    public TextTurn {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean isEmpty() {
        return line == null || line.isBlank();
    }
}
