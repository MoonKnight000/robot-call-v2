package uz.murodjon.robotcallv2.apikey.domain.service;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The allowlist is the whole point of the feature, so what is worth pinning down is what
 * a key can never hold — a permission that quietly becomes grantable is how a machine
 * credential ends up able to change what the platform charges.
 */
class ApiKeyScopesTest {

    @Test
    void neverGrantsBillingEdit() {
        assertThat(ApiKeyScopes.findGrantable()).doesNotContain(Permission.BILLING_EDIT);
        assertThat(ApiKeyScopes.intersect(Set.of(Permission.BILLING_READ, Permission.BILLING_EDIT)))
                .containsExactly(Permission.BILLING_READ);
    }

    @Test
    void neverGrantsConfigurationOrAccessManagement() {
        assertThat(ApiKeyScopes.findGrantable()).doesNotContainAnyElementsOf(List.of(
                Permission.AI_AGENT_EDIT, Permission.SCENARIO_EDIT, Permission.SIP_TRUNK_EDIT,
                Permission.AI_MODEL_EDIT, Permission.ENGINE_EDIT, Permission.VOICE_EDIT,
                Permission.INTEGRATION_EDIT, Permission.NOTIFICATION_SETTINGS_EDIT,
                Permission.USER_EDIT, Permission.ROLE_EDIT, Permission.COMPANY_EDIT,
                Permission.API_KEY_READ, Permission.API_KEY_EDIT,
                Permission.PLATFORM_ADMIN));
    }

    @Test
    void grantsTheDataScopesAnIntegrationNeeds() {
        assertThat(ApiKeyScopes.findGrantable()).contains(
                Permission.CONTACT_READ, Permission.CONTACT_EDIT,
                Permission.CAMPAIGN_READ, Permission.CAMPAIGN_EDIT,
                Permission.DO_NOT_CALL_READ, Permission.DO_NOT_CALL_EDIT,
                Permission.CALL_READ, Permission.REPORT_READ);
    }

    @Test
    void dropsEverythingOutsideTheAllowlist() {
        assertThat(ApiKeyScopes.intersect(Set.of(Permission.USER_EDIT, Permission.PLATFORM_ADMIN))).isEmpty();
        assertThat(ApiKeyScopes.intersect(null)).isEmpty();
        assertThat(ApiKeyScopes.intersect(Set.of())).isEmpty();
    }
}
