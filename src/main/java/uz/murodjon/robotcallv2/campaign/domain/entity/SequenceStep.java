package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;

import java.util.Map;

/**
 * One node of a campaign's sequence. Which fields apply depends on {@code type}; the rest are
 * null.
 *
 * @param id           unique inside the sequence; the routes point at it
 * @param conversation CONVERSATION only: who calls and how many times
 * @param smsText      SMS only: the text, with the contact's facts filled in
 * @param waitMinutes  WAIT only: how long the contact is held
 * @param routes       CONVERSATION only: the step each outcome leads to. An outcome with no entry
 *                     ends the sequence for that contact
 * @param next         SMS and WAIT only: the step that follows; null ends the sequence
 */
public record SequenceStep(
        String id,
        SequenceStepType type,
        String name,
        ConversationStepConfig conversation,
        String smsText,
        Integer waitMinutes,
        Map<SequenceOutcome, String> routes,
        String next,
        SequencePosition position
) {

    public SequenceStep {
        routes = routes == null ? Map.of() : Map.copyOf(routes);
    }
}
