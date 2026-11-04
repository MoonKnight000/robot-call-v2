package uz.murodjon.robotcallv2.chat.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.time.Instant;
import java.util.Map;

/**
 * What the post-chat pass made of a finished conversation — the same pass a call's transcript
 * goes through, against the agent's {@code dataNeeded} and the scenario's {@code outcomeSchema}.
 *
 * @param summary       null when the model gave none (the pass failed); the chat is not retried
 * @param outcome       the extracted fields, by id
 * @param needsFollowUp the person is waiting for something from the company
 */
public record ChatAnalysis(String summary, Sentiment sentiment, Boolean needsFollowUp,
                           Map<String, Object> outcome, Instant analyzedAt) {

    public ChatAnalysis {
        outcome = outcome != null ? Map.copyOf(outcome) : Map.of();
    }

    /** The pass could not be made; recorded so the chat is not tried again and again. */
    public static ChatAnalysis failed(Instant at) {
        return new ChatAnalysis(null, null, null, Map.of(), at);
    }
}
