package uz.murodjon.robotcallv2.role.domain.enums;

/**
 * The panel page a permission guards — the frontend renders the role editor grouped by
 * this, and translates each name itself (same contract as {@code ErrorCode}).
 *
 * <p>Each page belongs to one {@link Feature}: that is what ties a permission to the product
 * the platform can switch off for a company.
 */
public enum PermissionGroup {
    DASHBOARD(Feature.CORE),
    CAMPAIGN(Feature.VOICE_AGENT),
    CALL(Feature.VOICE_AGENT),
    LIVE(Feature.VOICE_AGENT),
    OPERATOR(Feature.VOICE_AGENT),
    CONTACT(Feature.VOICE_AGENT),
    /**
     * The agent builder serves the voice and the chat agent alike, so it is not either one's.
     */
    AI_AGENT(Feature.CORE),
    SCENARIO(Feature.CORE),
    REPORT(Feature.CORE),
    AUDIT(Feature.CORE),
    DO_NOT_CALL(Feature.VOICE_AGENT),
    INBOUND_ROUTE(Feature.VOICE_AGENT),
    CALL_FLOW(Feature.VOICE_AGENT),
    CALL_QUEUE(Feature.VOICE_AGENT),
    MISSED_CALL(Feature.VOICE_AGENT),
    SIP_TRUNK(Feature.VOICE_AGENT),
    USER(Feature.CORE),
    ROLE(Feature.CORE),
    COMPANY(Feature.CORE),
    AI_MODEL(Feature.CORE),
    ENGINE(Feature.CORE),
    VOICE(Feature.VOICE_AGENT),
    NOTIFICATION_SETTINGS(Feature.CORE),
    INTEGRATION(Feature.INTEGRATIONS),
    BILLING(Feature.CORE),
    API_KEY(Feature.CORE),
    QUALITY_REVIEW(Feature.QUALITY_ASSURANCE),
    CHAT_AGENT(Feature.CHAT_AGENT),
    ALERT(Feature.ALERTING),
    PLATFORM(Feature.CORE);

    private final Feature feature;

    PermissionGroup(Feature feature) {
        this.feature = feature;
    }

    public Feature feature() {
        return feature;
    }
}
