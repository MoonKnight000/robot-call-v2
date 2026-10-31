package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * The unit a step attempt's delay is written in, as the campaign screen shows it.
 */
public enum DelayUnit {
    MINUTES(1),
    HOURS(60),
    DAYS(24 * 60);

    private final int minutes;

    DelayUnit(int minutes) {
        this.minutes = minutes;
    }

    public long convertToMinutes(long amount) {
        return amount * minutes;
    }
}
