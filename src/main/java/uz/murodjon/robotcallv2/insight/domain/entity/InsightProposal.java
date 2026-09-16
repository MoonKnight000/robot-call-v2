package uz.murodjon.robotcallv2.insight.domain.entity;

import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;

import java.util.List;

/**
 * One finding as the clusterer hands it over: what it saw, and the one change it suggests.
 *
 * <p>Separate from {@link AgentInsight} because a proposal has no company, no agent and no
 * standing — those belong to the row the service writes from it, and keeping them out means
 * a model's answer cannot claim to be about an agent other than the one it was asked about.
 *
 * @param title       one line, the inbox's own words for the finding
 * @param detail      what was seen, with the numbers behind it
 * @param suggestion  the concrete change; for {@code PROMPT_FIX} exactly one guardrail sentence
 * @param callCount   how many of the window's calls back it
 * @param sampleCalls the call attempts it was read from — the evidence, not every call
 */
public record InsightProposal(
        InsightType type,
        String title,
        String detail,
        String suggestion,
        int callCount,
        List<Long> sampleCalls
) {
    public InsightProposal {
        sampleCalls = sampleCalls == null ? List.of() : List.copyOf(sampleCalls);
    }
}
