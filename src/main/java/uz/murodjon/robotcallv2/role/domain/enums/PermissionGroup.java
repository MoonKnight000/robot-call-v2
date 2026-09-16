package uz.murodjon.robotcallv2.role.domain.enums;

/**
 * The panel page a permission guards — the frontend renders the role editor grouped by
 * this, and translates each name itself (same contract as {@code ErrorCode}).
 */
public enum PermissionGroup {
    DASHBOARD,
    CAMPAIGN,
    CALL,
    LIVE,
    OPERATOR,
    CONTACT,
    AI_AGENT,
    SCENARIO,
    REPORT,
    AUDIT,
    DO_NOT_CALL,
    INBOUND_ROUTE,
    CALL_FLOW,
    CALL_QUEUE,
    MISSED_CALL,
    SIP_TRUNK,
    USER,
    ROLE,
    COMPANY,
    AI_MODEL,
    ENGINE,
    VOICE,
    NOTIFICATION_SETTINGS,
    INTEGRATION,
    BILLING,
    API_KEY,
    QUALITY_REVIEW,
    PLATFORM
}
