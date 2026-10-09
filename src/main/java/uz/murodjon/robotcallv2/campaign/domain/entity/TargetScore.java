package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * How likely a target is to pay, and when it usually answers — what a {@code SCORE} campaign
 * orders its due targets by.
 *
 * @param score        0–100; {@link #NEUTRAL} for a number with no history
 * @param bestCallHour the company-local hour (0–23) the number most often talked in, or null
 */
public record TargetScore(int score, Integer bestCallHour) {

    public static final int MIN = 0;
    public static final int MAX = 100;
    /** What a target not scored yet counts as. */
    public static final int NEUTRAL = 50;
    /** Added to the score while the company-local hour is the target's best one. */
    public static final int BEST_HOUR_BONUS = 20;
}
