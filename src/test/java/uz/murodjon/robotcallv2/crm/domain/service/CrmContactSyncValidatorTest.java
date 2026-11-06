package uz.murodjon.robotcallv2.crm.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactFieldMapping;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrmContactSyncValidatorTest {

    private static final Set<String> KEYS = Set.of("lead_status", "email");

    @Test
    void acceptsKnownDistinctFields() {
        assertThatCode(() -> CrmContactSyncValidator.validateMappings(List.of(
                new CrmContactFieldMapping("UF_CRM_1", "lead_status"),
                new CrmContactFieldMapping("EMAIL", "email")), KEYS)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownBlankAndDuplicate() {
        assertThatThrownBy(() -> CrmContactSyncValidator.validateMappings(List.of(
                new CrmContactFieldMapping("UF_CRM_1", "city")), KEYS))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CRM_CONTACT_SYNC_CONTACT_FIELD_UNKNOWN);
        assertThatThrownBy(() -> CrmContactSyncValidator.validateMappings(List.of(
                new CrmContactFieldMapping(" ", "email")), KEYS))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CRM_CONTACT_SYNC_CRM_FIELD_INVALID);
        assertThatThrownBy(() -> CrmContactSyncValidator.validateMappings(List.of(
                new CrmContactFieldMapping("A", "email"), new CrmContactFieldMapping("B", "email")), KEYS))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CRM_CONTACT_SYNC_CONTACT_FIELD_TWICE);
    }

    @Test
    void pushMappingsNeverWriteThePhoneNorOneCrmFieldTwice() {
        assertThatCode(() -> CrmContactSyncValidator.validatePushMappings(List.of(
                new CrmContactFieldMapping("UF_CRM_1", "lead_status"),
                new CrmContactFieldMapping("UF_CRM_2", "lead_status")), KEYS)).doesNotThrowAnyException();
        assertThatThrownBy(() -> CrmContactSyncValidator.validatePushMappings(List.of(
                new CrmContactFieldMapping("phone", "email")), KEYS))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CRM_CONTACT_SYNC_PHONE_NOT_WRITABLE);
        assertThatThrownBy(() -> CrmContactSyncValidator.validatePushMappings(List.of(
                new CrmContactFieldMapping("EMAIL", "email"), new CrmContactFieldMapping("EMAIL", "lead_status")), KEYS))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CRM_CONTACT_SYNC_CRM_FIELD_TWICE);
        assertThatThrownBy(() -> CrmContactSyncValidator.validatePushMappings(List.of(
                new CrmContactFieldMapping("EMAIL", "city")), KEYS))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CRM_CONTACT_SYNC_CONTACT_FIELD_UNKNOWN);
    }
}
