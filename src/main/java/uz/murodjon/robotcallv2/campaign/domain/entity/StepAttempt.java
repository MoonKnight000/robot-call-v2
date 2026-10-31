package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.DelayUnit;

/**
 * One call a CONVERSATION step places (Retell "Attempt N").
 *
 * @param delay how long after the previous attempt this one is placed, in {@code unit}; the
 *              step's first attempt goes out as soon as the contact reaches the step, so its
 *              delay is always 0. A later attempt with delay 0 waits the campaign's standard
 *              retry delay for the disposition, not nothing
 */
public record StepAttempt(int delay, DelayUnit unit) {

    public StepAttempt {
        delay = Math.max(0, delay);
        unit = unit != null ? unit : DelayUnit.MINUTES;
    }

    public static StepAttempt createImmediate() {
        return new StepAttempt(0, DelayUnit.MINUTES);
    }

    public long computeDelayMinutes() {
        return unit.convertToMinutes(delay);
    }
}
