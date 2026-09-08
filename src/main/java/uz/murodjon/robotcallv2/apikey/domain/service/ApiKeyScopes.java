package uz.murodjon.robotcallv2.apikey.domain.service;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * What an API key is allowed to do at most, whatever its row says.
 *
 * <p>The line drawn here is deliberate and worth stating: <b>a key moves data, it does not
 * change how the platform behaves or what it costs.</b> An integration may push contacts,
 * run campaigns, maintain the do-not-call list and read everything it produced; it may not
 * edit an agent, a scenario, a trunk, a model, a user, a role, or anything under billing.
 * Those decisions belong to a person who logged in and can be asked why.
 *
 * <p>The intersection is applied at authentication time, not when the key is issued, so a
 * right cannot be gained by editing the stored scopes — and a permission added to this
 * list later reaches existing keys only if they were granted it.
 */
public final class ApiKeyScopes {

    private static final Set<Permission> ALLOWED = Collections.unmodifiableSet(EnumSet.of(
            Permission.DASHBOARD_READ,
            Permission.CAMPAIGN_READ, Permission.CAMPAIGN_EDIT,
            Permission.CALL_READ,
            Permission.CONTACT_READ, Permission.CONTACT_EDIT,
            Permission.DO_NOT_CALL_READ, Permission.DO_NOT_CALL_EDIT,
            Permission.KNOWLEDGE_BASE_READ, Permission.KNOWLEDGE_BASE_EDIT,
            Permission.AI_AGENT_READ,
            Permission.SCENARIO_READ,
            Permission.INBOUND_ROUTE_READ,
            Permission.LIVE_READ,
            Permission.REPORT_READ,
            // Read-only on purpose: a key may show what a company is spending, never move
            // its money. BILLING_EDIT is not on this list and must not be added to it.
            Permission.BILLING_READ));

    private ApiKeyScopes() {
    }

    /** Everything a key may be granted — what the console offers when one is created. */
    public static Set<Permission> findGrantable() {
        return ALLOWED;
    }

    /** What a key actually holds: what it was granted, less anything no key may have. */
    public static Set<Permission> intersect(Collection<Permission> granted) {
        if (granted == null || granted.isEmpty()) {
            return Set.of();
        }
        EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
        for (Permission permission : granted) {
            if (permission != null && ALLOWED.contains(permission)) {
                effective.add(permission);
            }
        }
        return Collections.unmodifiableSet(effective);
    }
}
