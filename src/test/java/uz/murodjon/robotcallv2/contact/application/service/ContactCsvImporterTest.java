package uz.murodjon.robotcallv2.contact.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import uz.murodjon.robotcallv2.contact.application.dto.ContactCsvParseResult;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactCsvImporterTest {

    @ParameterizedTest
    @NullAndEmptySource
    void throwsValidationExceptionOnEmptyCsv(String csv) {
        assertThatThrownBy(() -> ContactCsvImporter.parse(csv))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void throwsValidationExceptionWhenPhoneColumnIsMissing() {
        String csv = """
                name,address,notes
                Ali Valiyev,Tashkent,VIP customer
                """;

        assertThatThrownBy(() -> ContactCsvImporter.parse(csv))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parsesStandardCommaSeparatedCsv() {
        String csv = """
                name,phone,address,tags,notes
                Ali Valiyev,+998901234567,Tashkent,"vip, retail",Important customer
                Vali Aliyev,+998911112233,Samarkand,retail,New client
                """;

        ContactCsvParseResult result = ContactCsvImporter.parse(csv);

        assertThat(result.contacts()).hasSize(2);
        assertThat(result.errors()).isEmpty();

        var c1 = result.contacts().get(0);
        assertThat(c1.name()).isEqualTo("Ali Valiyev");
        assertThat(c1.phone()).isEqualTo("+998901234567");
        assertThat(c1.address()).isEqualTo("Tashkent");
        assertThat(c1.tags()).isEqualTo("vip, retail");
        assertThat(c1.notes()).isEqualTo("Important customer");

        var c2 = result.contacts().get(1);
        assertThat(c2.name()).isEqualTo("Vali Aliyev");
        assertThat(c2.phone()).isEqualTo("+998911112233");
    }

    @Test
    void parsesSemicolonSeparatedWithUzbekHeaders() {
        String csv = """
                ism;telefon;manzil;teglar;izoh
                Jasur;998933334455;Buxoro;b2b;Shartnoma tuzildi
                """;

        ContactCsvParseResult result = ContactCsvImporter.parse(csv);

        assertThat(result.contacts()).hasSize(1);
        assertThat(result.errors()).isEmpty();
        var c = result.contacts().get(0);
        assertThat(c.name()).isEqualTo("Jasur");
        assertThat(c.phone()).isEqualTo("998933334455");
        assertThat(c.address()).isEqualTo("Buxoro");
        assertThat(c.tags()).isEqualTo("b2b");
        assertThat(c.notes()).isEqualTo("Shartnoma tuzildi");
    }

    @Test
    void tracksUnknownColumns() {
        String csv = """
                name,phone,custom_extra_column,age
                Dilshod,998901234567,xyz,30
                """;

        ContactCsvParseResult result = ContactCsvImporter.parse(csv);

        assertThat(result.contacts()).hasSize(1);
        assertThat(result.unknownColumns()).containsExactly("custom_extra_column", "age");
    }

    @Test
    void fallsBackNameToPhoneWhenNameIsEmpty() {
        String csv = """
                name,phone
                ,+998901234567
                """;

        ContactCsvParseResult result = ContactCsvImporter.parse(csv);

        assertThat(result.contacts()).hasSize(1);
        assertThat(result.contacts().get(0).name()).isEqualTo("+998901234567");
    }

    @Test
    void collectsErrorsForInvalidRowsWithoutFailingEntireBatch() {
        String csv = """
                name,phone
                Ali,998901234567
                Invalid Row Without Phone,
                Vali,998907654321
                """;

        ContactCsvParseResult result = ContactCsvImporter.parse(csv);

        assertThat(result.contacts()).hasSize(2);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).line()).isEqualTo(3);
    }
}
