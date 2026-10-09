package uz.murodjon.robotcallv2.alert.application.dto;

/**
 * What the rule would see if it were evaluated now. Nothing is sent and the rule's state is
 * not touched — for checking a threshold while setting it.
 *
 * @param value      null when the window holds nothing to compute it from
 * @param breached   the value is past the threshold with enough calls behind it
 * @param message    the notification text it would send
 */
public record AlertRulePreviewResponse(
        Double value,
        long sampleSize,
        boolean breached,
        String message
) {
}
