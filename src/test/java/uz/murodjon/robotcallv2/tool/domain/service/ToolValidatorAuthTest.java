package uz.murodjon.robotcallv2.tool.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolAuthType;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolValidatorAuthTest {

    @Test
    void aToolWithoutAuthIsFine() {
        assertThatCode(() -> ToolValidator.validateAuth(null)).doesNotThrowAnyException();
    }

    @Test
    void aClientNamesItsTokenEndpointAndBothSecrets() {
        assertThatCode(() -> ToolValidator.validateAuth(new ToolAuth(ToolAuthType.OAUTH2_CLIENT_CREDENTIALS,
                "https://crm.example/oauth/token", "CRM_ID", "CRM_SECRET", null))).doesNotThrowAnyException();

        assertThatThrownBy(() -> ToolValidator.validateAuth(new ToolAuth(ToolAuthType.OAUTH2_CLIENT_CREDENTIALS,
                " ", "CRM_ID", "CRM_SECRET", null))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ToolValidator.validateAuth(new ToolAuth(ToolAuthType.OAUTH2_CLIENT_CREDENTIALS,
                "https://crm.example/oauth/token", "CRM_ID", null, null))).isInstanceOf(ValidationException.class);
    }

    @Test
    void anAuthWithoutATypeIsRefused() {
        assertThatThrownBy(() -> ToolValidator.validateAuth(new ToolAuth(null, "https://crm.example/oauth/token",
                "CRM_ID", "CRM_SECRET", null))).isInstanceOf(ValidationException.class);
    }
}
