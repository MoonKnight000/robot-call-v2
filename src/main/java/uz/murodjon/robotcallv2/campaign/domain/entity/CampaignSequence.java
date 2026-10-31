package uz.murodjon.robotcallv2.campaign.domain.entity;

import java.util.List;
import java.util.Optional;

/**
 * What happens to a contact once it is in the campaign: the steps it goes through and which
 * step follows which. A contact starts at {@code startStepId} and the sequence ends for it when
 * a route leads nowhere.
 */
public record CampaignSequence(String startStepId, List<SequenceStep> steps) {

    public CampaignSequence {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public Optional<SequenceStep> findStep(String stepId) {
        return steps.stream().filter(step -> step.id().equals(stepId)).findFirst();
    }
}
