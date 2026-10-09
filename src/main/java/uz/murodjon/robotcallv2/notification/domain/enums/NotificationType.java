package uz.murodjon.robotcallv2.notification.domain.enums;

/**
 * Event types for notifications (UI-DESIGN §8.2 popover toggles).
 */
public enum NotificationType {
    CAMPAIGN_FINISHED,
    ERROR_OCCURRED,
    OPERATOR_REQUEST,
    DAILY_REPORT,
    /** A caller was not served — after hours, left the queue, or no operator was free. */
    MISSED_CALL,
    /** A caller left a voicemail. */
    VOICEMAIL_RECEIVED,
    /** A caller rated an operator call 1 or 2 out of 5. */
    LOW_SURVEY_SCORE,
    /** The dialer paused a campaign itself — no usable SIP trunk. */
    CAMPAIGN_PAUSED,
    /** A campaign's calls are being held because the company's balance cannot cover them. */
    BALANCE_LOW,
    /** A campaign's target source could not be fetched. */
    TARGET_SYNC_FAILED,
    /** A post-call webhook failed every retry (NEXT-PLAN S.6). */
    WEBHOOK_DELIVERY_FAILED,
    /** An accepted improvement passed the agent's test cases and is waiting to be published (D.4). */
    AGENT_INSIGHT_READY,
    /** An operator call scored below the company's threshold, or the client was upset (§2.3b). */
    SCORECARD_REVIEW,
    /** A company's own alert rule crossed its threshold (alert feature). */
    ALERT_TRIGGERED,
    /** Every concurrent call the company may hold is in use; the dialer waits for a free slot. */
    CONCURRENCY_LIMIT_REACHED
}
