package uz.murodjon.robotcallv2.contact.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldType;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactFieldValidatorTest {

    private static ContactField field(String key, ContactFieldType type, List<String> options,
                                      ContactFieldUpdateMode mode) {
        return ContactField.of(key, "Label", type, options, null, null, mode);
    }

    @Test
    void acceptsTextFieldWithDefaultMode() {
        assertThatCode(() -> ContactFieldValidator.validate(field("lead_status", ContactFieldType.TEXT, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsReservedAndMalformedKeys() {
        for (String key : List.of("phone", "Name", "contact_id", "external_ref", "1st", "_x", "a-b")) {
            assertThatThrownBy(() -> ContactFieldValidator.validate(field(key, ContactFieldType.TEXT, null, null)))
                    .as(key)
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.CONTACT_FIELD_KEY_INVALID);
        }
    }

    @Test
    void enumNeedsDistinctOptionsAndOthersTakeNone() {
        assertThatThrownBy(() -> ContactFieldValidator.validate(field("status", ContactFieldType.ENUM, List.of(), null)))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CONTACT_FIELD_OPTIONS_INVALID);
        assertThatThrownBy(() -> ContactFieldValidator.validate(
                field("status", ContactFieldType.ENUM, List.of("a", "a"), null)))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CONTACT_FIELD_OPTIONS_INVALID);
        assertThatThrownBy(() -> ContactFieldValidator.validate(
                field("age", ContactFieldType.NUMBER, List.of("1"), null)))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CONTACT_FIELD_OPTIONS_INVALID);
    }

    @Test
    void accumulateOnlyOnText() {
        assertThatThrownBy(() -> ContactFieldValidator.validate(
                field("age", ContactFieldType.NUMBER, null, ContactFieldUpdateMode.ACCUMULATE)))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CONTACT_FIELD_ACCUMULATE_TEXT_ONLY);
    }

    @Test
    void valuesMustFitTheirType() {
        assertThat(ContactFieldValidator.isValidValue(field("n", ContactFieldType.NUMBER, null, null), "12.5")).isTrue();
        assertThat(ContactFieldValidator.isValidValue(field("n", ContactFieldType.NUMBER, null, null), "abc")).isFalse();
        assertThat(ContactFieldValidator.isValidValue(field("b", ContactFieldType.BOOLEAN, null, null), "TRUE")).isTrue();
        assertThat(ContactFieldValidator.isValidValue(field("b", ContactFieldType.BOOLEAN, null, null), "ha")).isFalse();
        assertThat(ContactFieldValidator.isValidValue(field("e", ContactFieldType.ENUM, List.of("hot"), null), "hot")).isTrue();
        assertThat(ContactFieldValidator.isValidValue(field("e", ContactFieldType.ENUM, List.of("hot"), null), "cold")).isFalse();
        assertThat(ContactFieldValidator.isValidValue(field("d", ContactFieldType.DATE, null, null), "2026-11-06")).isTrue();
        assertThat(ContactFieldValidator.isValidValue(field("d", ContactFieldType.DATE, null, null), "06.11.2026")).isFalse();
        assertThat(ContactFieldValidator.isValidValue(field("t", ContactFieldType.DATETIME, null, null),
                "2026-11-06T09:00:00+05:00")).isTrue();
        assertThat(ContactFieldValidator.isValidValue(field("t", ContactFieldType.DATETIME, null, null), "tomorrow")).isFalse();
    }

    @Test
    void undefinedKeysStayFreeText() {
        List<ContactField> fields = List.of(field("age", ContactFieldType.NUMBER, null, null));
        assertThatCode(() -> ContactFieldValidator.validateValues(fields, Map.of("note", "anything", "age", "30")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ContactFieldValidator.validateValues(fields, Map.of("age", "thirty")))
                .hasFieldOrPropertyWithValue("code", ErrorCode.CONTACT_FIELD_VALUE_INVALID);
    }
}
