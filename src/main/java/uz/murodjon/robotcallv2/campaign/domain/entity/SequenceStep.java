package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;

/**
 * One node of a campaign's sequence. Which fields apply depends on {@code type}; the rest are
 * null.
 *
 * @param id           unique inside the sequence; outcome routes point at it
 * @param conversation CONVERSATION only: who calls and how many times
 * @param smsText      SMS only: the text, with the contact's facts filled in
 * @param waitMinutes  WAIT only: how long the contact is held
 * @param outcomes     CONVERSATION only: the step's own outcomes and its built-in Other and No
 *                     Response routes; {@link StepOutcomes#NONE} for every other type
 * @param next         SMS and WAIT only: the step that follows; null ends the sequence
 */
public record SequenceStep(
        String id,
        SequenceStepType type,
        String name,
        ConversationStepConfig conversation,
        String smsText,
        Integer waitMinutes,
        StepOutcomes outcomes,
        String next,
        SequencePosition position
) {

    public SequenceStep {
        outcomes = outcomes != null ? outcomes : StepOutcomes.NONE;
    }
}
