package uz.murodjon.robotcallv2.quality.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.util.List;

/**
 * What the judge made of an operator call: one answer per criterion, how the client came
 * across, what the call was about, and the tokens it took to say so.
 *
 * @param sentiment null when the judge did not say
 * @param topics    one to three short lowercase tags; empty when the judge gave none
 */
public record ScorecardVerdict(
        List<CallScorecardAnswer> answers,
        Sentiment sentiment,
        List<String> topics,
        long promptTokens,
        long completionTokens,
        long cachedTokens
) {

    public ScorecardVerdict {
        answers = answers == null ? List.of() : List.copyOf(answers);
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
