package uz.murodjon.robotcallv2.dialer.domain.entity;

/**
 * What a predictive campaign's recent calls did, over the window its pace is set from.
 *
 * @param dialled      calls placed
 * @param answered     of those, picked up by a person (machines are not counted)
 * @param aiOverflowed of those answered, taken by the AI agent because no operator was free
 */
public record DialPacingStats(int dialled, int answered, int aiOverflowed) {

    public static final DialPacingStats EMPTY = new DialPacingStats(0, 0, 0);
}
