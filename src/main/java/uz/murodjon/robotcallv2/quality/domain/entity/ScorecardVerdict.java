package uz.murodjon.robotcallv2.quality.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.util.List;

/**
 * What the judge made of an operator call: one answer per criterion, how the client came
 * across, what the call was about, and the tokens it took to say so.
 *
 * @param sentiment null when the judge did not say
 * @param topics    one to three short lowercase tags; empty when the judge gave none
 * @param model     the model id the provider reported; null when unknown (billed at the common rate)
 */
public record ScorecardVerdict(
        List<CallScorecardAnswer> answers,
        Sentiment sentiment,
        List<String> topics,
        long promptTokens,
        long completionTokens,
        long cachedTokens,
        String model
) {

    /** A verdict whose model is not known. */
    public ScorecardVerdict(List<CallScorecardAnswer> answers, Sentiment sentiment, List<String> topics,
                            long promptTokens, long completionTokens, long cachedTokens) {
        this(answers, sentiment, topics, promptTokens, completionTokens, cachedTokens, null);
    }

    public ScorecardVerdict {
        answers = answers == null ? List.of() : List.copyOf(answers);
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
