package uz.murodjon.robotcallv2.campaign.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.application.dto.CsvColumnMapping;
import uz.murodjon.robotcallv2.campaign.application.dto.ParsedTarget;
import uz.murodjon.robotcallv2.campaign.application.dto.TargetCsvParseResult;
import uz.murodjon.robotcallv2.campaign.application.service.TargetCsvImporter;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CSV target list parser.
 */
class TargetCsvImporterTest {

    @Test
    void parsesStandardCsvFile() {
        String csv = """
                clientId,phone,language,clientName,debtAmount,currency,dueDate,contractNumber
                1001,+998901234567,uz-UZ,Aziz Karimov,1500000,so'm,2026-07-01,UY-2026-00123
                1002,998912345678,ru-RU,Ivan Petrov,2500000,so'm,2026-08-01,UY-2026-00124
                """;

        TargetCsvParseResult result = TargetCsvImporter.parse(csv);

        assertThat(result.errors()).isEmpty();
        assertThat(result.unknownColumns()).isEmpty();
        assertThat(result.targets()).hasSize(2);

        ParsedTarget t1 = result.targets().get(0);
        assertThat(t1.clientId()).isEqualTo(1001L);
        assertThat(t1.phone()).isEqualTo("+998901234567");
        assertThat(t1.language()).isEqualTo("uz-UZ");
        assertThat(t1.contextJson()).contains("\"clientName\":\"Aziz Karimov\"");
        assertThat(t1.contextJson()).contains("\"debtAmount\":\"1500000\"");
    }

    @Test
    void mapsKnownAndUnknownColumnsForPreview() {
        String csv = """
                clientId,phone,extraHeader
                1001,+998901234567,someValue
                """;

        List<CsvColumnMapping> mappings = TargetCsvImporter.mapColumns(csv);

        assertThat(mappings).hasSize(3);
        assertThat(mappings.get(0).mappedField()).isEqualTo("clientId");
        assertThat(mappings.get(1).mappedField()).isEqualTo("phone");
        assertThat(mappings.get(2).mappedField()).isNull();
    }

    @Test
    void missingLanguageMeansTheCampaignDefault() {
        TargetCsvParseResult result = TargetCsvImporter.parse("""
                phone,language
                998901234567,
                """);

        assertThat(result.targets().get(0).language()).isNull();
    }

    @Test
    void rejectsAFileWithNoPhoneColumn() {
        assertThatThrownBy(() -> TargetCsvImporter.parse("clientId,clientName\n1,Aziz\n"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> TargetCsvImporter.parse("  "))
                .isInstanceOf(ValidationException.class);
    }
}
