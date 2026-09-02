package uz.murodjon.robotcallv2.live.domain.enums;

/**
 * SSE {@code event:} names published on {@code GET /api/live/stream}
 * (docs/API-REQUIREMENTS.md §0.7).
 */
public enum LiveEventType {
    /** Payload: {@link LiveKpiSnapshot}. */
    KPI,
    /** Payload: {@code List<uz.murodjon.robotcallv2.callrecord.dto.LiveCallRow>}. */
    LIVE_CALLS,
    /** Payload: {@link LiveTranscriptEvent}. */
    TRANSCRIPT,
    /** Payload: {@link AudioLevelEvent}. */
    AUDIO_LEVEL,
    /** Payload: {@link LiveNotification}. */
    NOTIFICATION
}

