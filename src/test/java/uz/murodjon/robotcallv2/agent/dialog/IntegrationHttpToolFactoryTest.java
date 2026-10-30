package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The variables an integration tool can read besides the call's own. */
class IntegrationHttpToolFactoryTest {

    private static final CrmSettings NONE = new CrmSettings(7, null, null, null, null, null, null, null, null);

    @Test
    void thePhoneIsAlsoOfferedAsItsLastNineDigits() {
        Map<String, Object> variables = IntegrationHttpToolFactory.withCrmVariables(
                Map.of("phone", "+998 90 123-45-67"), NONE);

        assertThat(variables).containsEntry("phone", "+998 90 123-45-67").containsEntry("phone_last9", "901234567");
    }

    @Test
    void aShortNumberIsKeptWhole() {
        assertThat(IntegrationHttpToolFactory.withCrmVariables(Map.of("phone", "12345"), NONE))
                .containsEntry("phone_last9", "12345");
    }

    @Test
    void noPhoneMeansNoPhoneSuffix() {
        assertThat(IntegrationHttpToolFactory.withCrmVariables(Map.of(), NONE)).doesNotContainKey("phone_last9");
    }

    @Test
    void theCompanysCrmSettingsAreOfferedAndWhatIsUnsetIsLeftOut() {
        CrmSettings settings = new CrmSettings(7, 55, null, null, null, null, 301, 9, null);

        Map<String, Object> variables = IntegrationHttpToolFactory.withCrmVariables(Map.of(), settings);

        assertThat(variables).containsEntry("crm_ai_employee_id", 55)
                .containsEntry("crm_new_lead_status_id", 301)
                .containsEntry("crm_task_type_id", 9)
                .doesNotContainKeys("crm_callback_status_id", "crm_meeting_booked_status_id");
    }

    @Test
    void theCallsOwnVariablesAreNotChanged() {
        Map<String, Object> own = Map.of("phone", "+998901234567");

        IntegrationHttpToolFactory.withCrmVariables(own, NONE);

        assertThat(own).containsOnlyKeys("phone");
    }
}
