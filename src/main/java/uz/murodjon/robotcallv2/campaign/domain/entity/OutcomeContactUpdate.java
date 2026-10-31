package uz.murodjon.robotcallv2.campaign.domain.entity;

import java.util.List;

/**
 * What a matched outcome writes to the contact (Retell "Save outcome to contact field"): fixed
 * values set here, never data taken from the call.
 *
 * @param doNotCall puts the number on the company's do-not-call list
 * @param tags      added to the contact with this number, so later campaigns can filter on them
 */
public record OutcomeContactUpdate(boolean doNotCall, List<String> tags) {

    public static final OutcomeContactUpdate NONE = new OutcomeContactUpdate(false, List.of());

    public OutcomeContactUpdate {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
