package uz.murodjon.robotcallv2.live.domain.entity;

import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;

/**
 * A live event on its way between instances: published where the call runs, delivered to
 * the streams every other instance holds for the same company.
 *
 * @param payload the event's own record ({@link LiveTranscriptEvent}, {@link AudioLevelEvent},
 *                {@link LiveNotification})
 */
public record LiveRelayEvent(long companyId, LiveEventType type, Object payload) {
}
