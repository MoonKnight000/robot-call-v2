package uz.murodjon.robotcallv2.role.domain.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A product the platform sells and switches on or off for a company. Every permission belongs
 * to exactly one feature (through its {@link PermissionGroup}); when a company's feature is off,
 * nobody in that company holds the feature's permissions, whatever their role says.
 *
 * <p>{@link #CORE} is the platform itself — dashboard, reports, audit, users, roles, company,
 * billing, API keys, models, notification channels — and the agent builder (agents, scenarios,
 * knowledge base) that the voice and the chat agent share. It cannot be switched off.
 *
 * <p>A company stores only the features it has switched off, so a feature added in a later
 * release is on for every existing company without a data migration.
 */
public enum Feature {

    CORE(false),
    /** Outbound and inbound phone calls: campaigns, dialer, live calls, queues, SIP. */
    VOICE_AGENT(true),
    /** Text agents answering in Telegram and the website widget. */
    CHAT_AGENT(true),
    /** Alert rules on call metrics. */
    ALERTING(true),
    /** CRM and knowledge-source connections. */
    INTEGRATIONS(true),
    /** The review inbox and operator scorecards. */
    QUALITY_ASSURANCE(true);

    private final boolean switchable;

    Feature(boolean switchable) {
        this.switchable = switchable;
    }

    /** False only for {@link #CORE}. */
    public boolean switchable() {
        return switchable;
    }

    /** What the platform can turn off — what the company form offers. */
    public static Set<Feature> findSwitchable() {
        return Arrays.stream(values()).filter(Feature::switchable).collect(Collectors.toUnmodifiableSet());
    }
}
