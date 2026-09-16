package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.List;

/**
 * One simulated call as {@link AgentSimulator} played it.
 *
 * @param transcript  the lines in order, each prefixed {@code AGENT: } or {@code CLIENT: }
 * @param disposition how the agent filed the call, or null when it never settled one
 * @param stage       the stage the conversation ended in
 * @param problems    what went wrong on the way — a silent turn, a figure the fact guard
 *                    caught; empty when nothing did
 */
public record SimulatedCall(
        List<String> transcript,
        Disposition disposition,
        String stage,
        List<String> problems
) {

    public SimulatedCall {
        transcript = transcript == null ? List.of() : List.copyOf(transcript);
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    static SimulatedCall failed(String problem) {
        return new SimulatedCall(List.of(), null, null, List.of(problem));
    }

    public String transcriptText() {
        return String.join("\n", transcript);
    }
}
