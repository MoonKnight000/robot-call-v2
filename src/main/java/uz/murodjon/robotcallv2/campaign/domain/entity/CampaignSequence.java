package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;

import java.util.List;
import java.util.Optional;

/**
 * What happens to a contact once it is in the campaign: the steps it goes through and which
 * step follows which. A contact starts at {@code startStepId} and the sequence ends for it when
 * a route leads out of it.
 *
 * @param globalOutcomes outcomes every CONVERSATION step is judged by, after its own (Retell
 *                       "Global outcomes")
 */
public record CampaignSequence(String startStepId, List<SequenceStep> steps, List<SequenceOutcome> globalOutcomes) {

    public CampaignSequence {
        steps = steps == null ? List.of() : List.copyOf(steps);
        globalOutcomes = globalOutcomes == null ? List.of() : List.copyOf(globalOutcomes);
    }

    /**
     * What a new empty draft starts with, as in Retell: one conversation step with no agent yet
     * (it is asked for at launch), four attempts on days 0, 3, 10 and 20, and the default global outcomes. Other and No
     * Response end the sequence.
     */
    public static CampaignSequence createDefault() {
        SequenceStep conversation = new SequenceStep("step1", SequenceStepType.CONVERSATION, "Suhbat 1",
                new ConversationStepConfig(null, ConversationStepConfig.DEFAULT_ATTEMPTS), null, null, StepOutcomes.NONE,
                null, null);
        return new CampaignSequence(conversation.id(), List.of(conversation), SequenceOutcome.createDefaults());
    }

    public Optional<SequenceStep> findStep(String stepId) {
        return steps.stream().filter(step -> step.id().equals(stepId)).findFirst();
    }
}
