package uz.murodjon.robotcallv2.role.domain.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every right the panel can grant, one pair per page: {@code *_READ} opens the page,
 * {@code *_EDIT} allows creating, changing and deleting on it.
 *
 * <p>Two identifiers travel with each constant and they are not interchangeable. The
 * {@link #name()} is what the database stores, what {@code @PreAuthorize} checks and what
 * the frontend translates — readable and safe to reason about. The {@link #code()} is the
 * short form the access token carries, so a role with forty permissions costs a few
 * hundred bytes of JWT instead of a kilobyte. <b>A code is part of every token already
 * issued: renaming one silently changes what an old token grants, so codes are append-only.</b>
 */
public enum Permission {

    DASHBOARD_READ("dsh.r", PermissionGroup.DASHBOARD),

    CAMPAIGN_READ("cmp.r", PermissionGroup.CAMPAIGN),
    CAMPAIGN_EDIT("cmp.w", PermissionGroup.CAMPAIGN),

    CALL_READ("cal.r", PermissionGroup.CALL),
    CALL_EDIT("cal.w", PermissionGroup.CALL),

    LIVE_READ("liv.r", PermissionGroup.LIVE),
    LIVE_EDIT("liv.w", PermissionGroup.LIVE),

    OPERATOR_READ("opr.r", PermissionGroup.OPERATOR),
    OPERATOR_EDIT("opr.w", PermissionGroup.OPERATOR),

    CONTACT_READ("cnt.r", PermissionGroup.CONTACT),
    CONTACT_EDIT("cnt.w", PermissionGroup.CONTACT),

    AI_AGENT_READ("aag.r", PermissionGroup.AI_AGENT),
    AI_AGENT_EDIT("aag.w", PermissionGroup.AI_AGENT),

    SCENARIO_READ("scn.r", PermissionGroup.SCENARIO),
    SCENARIO_EDIT("scn.w", PermissionGroup.SCENARIO),

    KNOWLEDGE_BASE_READ("kb.r", PermissionGroup.SCENARIO),
    KNOWLEDGE_BASE_EDIT("kb.w", PermissionGroup.SCENARIO),


    REPORT_READ("rpt.r", PermissionGroup.REPORT),
    REPORT_EDIT("rpt.w", PermissionGroup.REPORT),

    AUDIT_READ("adt.r", PermissionGroup.AUDIT),

    DO_NOT_CALL_READ("dnc.r", PermissionGroup.DO_NOT_CALL),
    DO_NOT_CALL_EDIT("dnc.w", PermissionGroup.DO_NOT_CALL),

    INBOUND_ROUTE_READ("inb.r", PermissionGroup.INBOUND_ROUTE),
    INBOUND_ROUTE_EDIT("inb.w", PermissionGroup.INBOUND_ROUTE),

    SIP_TRUNK_READ("sip.r", PermissionGroup.SIP_TRUNK),
    SIP_TRUNK_EDIT("sip.w", PermissionGroup.SIP_TRUNK),

    USER_READ("usr.r", PermissionGroup.USER),
    USER_EDIT("usr.w", PermissionGroup.USER),

    ROLE_READ("rol.r", PermissionGroup.ROLE),
    ROLE_EDIT("rol.w", PermissionGroup.ROLE),

    COMPANY_READ("cny.r", PermissionGroup.COMPANY),
    COMPANY_EDIT("cny.w", PermissionGroup.COMPANY),

    AI_MODEL_READ("aim.r", PermissionGroup.AI_MODEL),
    AI_MODEL_EDIT("aim.w", PermissionGroup.AI_MODEL),

    ENGINE_READ("eng.r", PermissionGroup.ENGINE),
    ENGINE_EDIT("eng.w", PermissionGroup.ENGINE),

    VOICE_READ("vce.r", PermissionGroup.VOICE),
    VOICE_EDIT("vce.w", PermissionGroup.VOICE),

    NOTIFICATION_SETTINGS_READ("nts.r", PermissionGroup.NOTIFICATION_SETTINGS),
    NOTIFICATION_SETTINGS_EDIT("nts.w", PermissionGroup.NOTIFICATION_SETTINGS),

    INTEGRATION_READ("itg.r", PermissionGroup.INTEGRATION),
    INTEGRATION_EDIT("itg.w", PermissionGroup.INTEGRATION),

    BILLING_READ("bil.r", PermissionGroup.BILLING),
    BILLING_EDIT("bil.w", PermissionGroup.BILLING),

    /** Tenant onboarding and suspension — platform staff only, never grantable to a company role. */
    PLATFORM_ADMIN("plt.a", PermissionGroup.PLATFORM, PermissionScope.PLATFORM);

    private static final Map<String, Permission> BY_CODE = indexByCode();

    private final String code;
    private final PermissionGroup group;
    private final PermissionScope scope;

    Permission(String code, PermissionGroup group) {
        this(code, group, PermissionScope.COMPANY);
    }

    Permission(String code, PermissionGroup group, PermissionScope scope) {
        this.code = code;
        this.group = group;
        this.scope = scope;
    }

    public String code() {
        return code;
    }

    public PermissionGroup group() {
        return group;
    }

    public PermissionScope scope() {
        return scope;
    }

    /** Reverse of {@link #code()} — {@code null} for a code no longer known (stale token). */
    public static Permission findByCode(String code) {
        return BY_CODE.get(code);
    }

    /** Everything a company role may hold — what DEVELOPER always gets, new entries included. */
    public static Set<Permission> findCompanyPermissions() {
        return filterByScope(PermissionScope.COMPANY);
    }

    private static Set<Permission> filterByScope(PermissionScope scope) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Permission permission : values()) {
            if (permission.scope == scope) {
                result.add(permission);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static Map<String, Permission> indexByCode() {
        Map<String, Permission> index = new HashMap<>();
        Arrays.stream(values()).forEach(permission -> {
            if (index.put(permission.code, permission) != null) {
                throw new IllegalStateException("Duplicate permission code: " + permission.code);
            }
        });
        return Map.copyOf(index);
    }
}
