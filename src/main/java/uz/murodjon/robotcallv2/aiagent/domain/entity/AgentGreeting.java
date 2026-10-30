package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.GreetingMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * How a call opens: who speaks first, how long the agent waits before it does, and the lines it
 * may open with. The agent's {@code firstMessage} stays the baseline line; {@code lines} add
 * openings for particular languages and directions, or several interchangeable ones so that
 * callers do not hear the identical sentence every time.
 *
 * @param mode                  who speaks first; never null
 * @param pauseBeforeSpeakingMs quiet time between answering and the agent's first words;
 *                              null = the platform default ({@code greeting-delay-ms})
 * @param lines                 the lines besides {@code firstMessage}; never null
 */
public record AgentGreeting(GreetingMode mode, Integer pauseBeforeSpeakingMs, List<GreetingLine> lines) {

    public static final AgentGreeting DEFAULT = new AgentGreeting(null, null, null);

    public AgentGreeting {
        if (mode == null) {
            mode = GreetingMode.AI_CUSTOM;
        }
        lines = lines == null ? List.of() : lines.stream().filter(line -> !line.text().isEmpty()).toList();
    }

    /**
     * The line to open this call with, or null when the agent should compose one.
     *
     * <p>The baseline {@code firstMessage} competes as a line aimed at nothing in particular. The
     * most narrowly aimed lines that suit the call win; among equals one is picked at random,
     * which is what makes several lines into variants.
     *
     * @param pick returns a number below its argument — injectable so the choice can be tested
     */
    public String findLine(String firstMessage, String callLanguage, boolean inbound, IntUnaryOperator pick) {
        if (mode != GreetingMode.AI_CUSTOM) {
            return null;
        }
        List<GreetingLine> candidates = new ArrayList<>(lines);
        candidates.add(new GreetingLine(null, null, firstMessage));
        List<GreetingLine> suitable = candidates.stream()
                .filter(line -> !line.text().isEmpty() && line.matches(callLanguage, inbound))
                .toList();
        if (suitable.isEmpty()) {
            return null;
        }
        int narrowest = suitable.stream().mapToInt(GreetingLine::specificity).max().getAsInt();
        List<GreetingLine> winners = suitable.stream().filter(line -> line.specificity() == narrowest).toList();
        return winners.get(pick.applyAsInt(winners.size())).text();
    }

    /** The same greeting set to say exactly one line — an explicit override of the opening. */
    public AgentGreeting speakingOnlyFirstMessage() {
        return new AgentGreeting(GreetingMode.AI_CUSTOM, pauseBeforeSpeakingMs, List.of());
    }
}
