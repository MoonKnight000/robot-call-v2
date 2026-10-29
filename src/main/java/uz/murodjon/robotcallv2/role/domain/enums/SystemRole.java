package uz.murodjon.robotcallv2.role.domain.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The roles every company is seeded with. Their permissions are computed here rather than
 * stored as rows, so a permission added in a later release reaches them without a data
 * migration — and so nobody can quietly widen ADMIN by editing a table.
 *
 * <p>DEVELOPER and SUPERADMIN are deliberately not assignable by a tenant's own admin:
 * DEVELOPER is our engineer sitting inside a customer's company with every company right,
 * SUPERADMIN is platform staff and additionally holds {@link Permission#PLATFORM_ADMIN}.
 */
public enum SystemRole {

    DEVELOPER("Developer", false),
    ADMIN("Administrator", true),
    OPERATOR("Operator", true),
    VIEWER("Viewer", true),
    SUPERADMIN("Superadmin", false),
    /** Runs the operator floor: everything an operator does, plus monitoring, queue membership and the call review inbox. */
    SUPERVISOR("Supervisor", true);

    private final String label;
    private final boolean assignableByCompany;

    SystemRole(String label, boolean assignableByCompany) {
        this.label = label;
        this.assignableByCompany = assignableByCompany;
    }

    public String label() {
        return label;
    }

    /** False for the two roles only platform staff may hand out (DEVELOPER, SUPERADMIN). */
    public boolean assignableByCompany() {
        return assignableByCompany;
    }

    public Set<Permission> resolvePermissions() {
        return switch (this) {
            case DEVELOPER -> Permission.findCompanyPermissions();
            case SUPERADMIN -> platformPermissions();
            // Everything a tenant's own boss needs; engine tuning stays with DEVELOPER
            // because a wrong value there breaks every call in the company.
            case ADMIN -> withoutEngineEdit();
            case OPERATOR -> EnumSet.of(
                    Permission.DASHBOARD_READ,
                    Permission.CAMPAIGN_READ, Permission.CAMPAIGN_EDIT,
                    Permission.CALL_READ, Permission.CALL_EDIT,
                    Permission.LIVE_READ, Permission.LIVE_EDIT,
                    Permission.OPERATOR_READ, Permission.OPERATOR_EDIT,
                    Permission.CONTACT_READ, Permission.CONTACT_EDIT,
                    Permission.AI_AGENT_READ,
                    Permission.CHAT_AGENT_READ,
                    Permission.SCENARIO_READ, Permission.KNOWLEDGE_BASE_READ,
                    Permission.REPORT_READ,
                    Permission.DO_NOT_CALL_READ, Permission.DO_NOT_CALL_EDIT,
                    Permission.INBOUND_ROUTE_READ,
                    Permission.CALL_QUEUE_READ,
                    Permission.MISSED_CALL_READ, Permission.MISSED_CALL_EDIT,
                    Permission.VOICE_READ);
            case SUPERVISOR -> EnumSet.of(
                    Permission.DASHBOARD_READ,
                    Permission.CAMPAIGN_READ, Permission.CAMPAIGN_EDIT,
                    Permission.CALL_READ, Permission.CALL_EDIT,
                    Permission.LIVE_READ, Permission.LIVE_EDIT, Permission.CALL_MONITOR,
                    Permission.OPERATOR_READ, Permission.OPERATOR_EDIT,
                    Permission.CONTACT_READ, Permission.CONTACT_EDIT,
                    Permission.AI_AGENT_READ,
                    Permission.CHAT_AGENT_READ,
                    Permission.SCENARIO_READ, Permission.KNOWLEDGE_BASE_READ,
                    Permission.REPORT_READ,
                    Permission.DO_NOT_CALL_READ, Permission.DO_NOT_CALL_EDIT,
                    Permission.INBOUND_ROUTE_READ,
                    Permission.CALL_FLOW_READ,
                    Permission.CALL_QUEUE_READ, Permission.CALL_QUEUE_EDIT,
                    Permission.MISSED_CALL_READ, Permission.MISSED_CALL_EDIT,
                    Permission.USER_READ,
                    Permission.VOICE_READ,
                    Permission.QUALITY_REVIEW_READ, Permission.QUALITY_REVIEW_EDIT);
            case VIEWER -> EnumSet.of(
                    Permission.DASHBOARD_READ,
                    Permission.CAMPAIGN_READ,
                    Permission.CALL_READ,
                    Permission.LIVE_READ,
                    Permission.CONTACT_READ,
                    Permission.AI_AGENT_READ,
                    Permission.CHAT_AGENT_READ,
                    Permission.SCENARIO_READ, Permission.KNOWLEDGE_BASE_READ,
                    Permission.REPORT_READ,
                    Permission.DO_NOT_CALL_READ,
                    Permission.INBOUND_ROUTE_READ,
                    Permission.CALL_QUEUE_READ);
        };
    }

    public static SystemRole findByCode(String code) {
        for (SystemRole role : values()) {
            if (role.name().equals(code)) {
                return role;
            }
        }
        return null;
    }

    private static Set<Permission> platformPermissions() {
        EnumSet<Permission> all = EnumSet.copyOf(Permission.findCompanyPermissions());
        all.add(Permission.PLATFORM_ADMIN);
        return Collections.unmodifiableSet(all);
    }

    private static Set<Permission> withoutEngineEdit() {
        EnumSet<Permission> permissions = EnumSet.copyOf(Permission.findCompanyPermissions());
        permissions.remove(Permission.ENGINE_EDIT);
        return Collections.unmodifiableSet(permissions);
    }
}
