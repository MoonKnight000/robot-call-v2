package uz.murodjon.robotcallv2.callrecord.domain.entity;

import java.util.Map;

/**
 * A company's outbound calls to one number over a period, as the target scoring reads them.
 *
 * @param placed        calls that reached the trunk
 * @param reached       of those, the ones a person actually talked on (not a voicemail, a wrong
 *                      number or a no-answer)
 * @param refused       of those, the ones that ended {@code REFUSED}
 * @param reachedByHour company-local hour (0–23) → how many of the reached calls were answered in it
 */
public record PhoneCallHistory(int placed, int reached, int refused, Map<Integer, Integer> reachedByHour) {

    public static final PhoneCallHistory NONE = new PhoneCallHistory(0, 0, 0, Map.of());

    public PhoneCallHistory {
        reachedByHour = reachedByHour == null ? Map.of() : Map.copyOf(reachedByHour);
    }
}
