package uz.murodjon.uysotvoice.campaign.service;

import org.junit.jupiter.api.Test;

import uz.murodjon.uysotvoice.campaign.dto.ParsedTarget;
import uz.murodjon.uysotvoice.campaign.dto.TargetCsvParseResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The file this parses comes out of a CRM export, so the interesting cases are the ones a
 * spreadsheet produces: quoted fields, a shifted column order, a semicolon locale, and the
 * handful of bad rows that must not take the other 1 997 down with them.
 */
class TargetCsvImporterTest {

    @Test
    void parsesTheDocumentedLayout() {
        TargetCsvParseResult result = TargetCsvImporter.parse("""
                clientId,phone,language,clientName,debtAmount,currency,dueDate,contractNumber
                1001,998901234567,uz-UZ,Aziz Karimov,1500000,so'm,2026-07-01,UY-2026-00123
                """);

        assertThat(result.errors()).isEmpty();
        assertThat(result.targets()).hasSize(1);
        ParsedTarget t = result.targets().get(0);
        assertThat(t.clientId()).isEqualTo(1001L);
        assertThat(t.phone()).isEqualTo("998901234567");
        assertThat(t.language()).isEqualTo("uz-UZ");
        assertThat(t.contextJson())
                .contains("\"clientName\":\"Aziz Karimov\"")
                .contains("\"debtAmount\":\"1500000\"")
                .contains("\"contractNumber\":\"UY-2026-00123\"");
    }

    @Test
    void columnsAreFoundByNameNotPosition() {
        // A CRM export's column order is not a contract, and a silently shifted column would
        // put a debt amount in the phone field.
        TargetCsvParseResult result = TargetCsvImporter.parse("""
                phone,clientName,clientId
                998901234567,Aziz,7
                """);

        assertThat(result.targets()).hasSize(1);
        assertThat(result.targets().get(0).phone()).isEqualTo("998901234567");
        assertThat(result.targets().get(0).clientId()).isEqualTo(7L);
    }

    @Test
    void headerNamesTolerateCaseAndSpacing() {
        TargetCsvParseResult result = TargetCsvImporter.parse("""
                Phone,Client ID,Debt_Amount
                998901234567,7,1500000
                """);

        assertThat(result.targets()).hasSize(1);
        assertThat(result.targets().get(0).clientId()).isEqualTo(7L);
        assertThat(result.targets().get(0).contextJson()).contains("\"debtAmount\":\"1500000\"");
    }

    @Test
    void handlesQuotedFieldsWithCommasAndQuotes() {
        TargetCsvParseResult result = TargetCsvImporter.parse("""
                phone,clientName,goal
                998901234567,"Karimov, Aziz","He said ""later""\"
                """);

        assertThat(result.targets()).hasSize(1);
        assertThat(result.targets().get(0).contextJson())
                .contains("Karimov, Aziz")
                .contains("He said \\\"later\\\"");
    }

    @Test
    void handlesASemicolonSeparatedExport() {
        // What a spreadsheet writes where the comma is the decimal separator — and a common
        // way to lose an entire import to a single mis-split column.
        TargetCsvParseResult result = TargetCsvImporter.parse("""
                clientId;phone;clientName
                7;998901234567;Aziz
                """);

        assertThat(result.targets()).hasSize(1);
        assertThat(result.targets().get(0).phone()).isEqualTo("998901234567");
    }

    @Test
    void badRowsAreReportedAndTheRestStillLoad() {
        TargetCsvParseResult result = TargetCsvImporter.parse("""
                clientId,phone
                1,998901234567
                2,
                notanumber,998901234568
                4,998901234569
                """);

        assertThat(result.targets()).hasSize(2);
        assertThat(result.errors()).hasSize(2);
        // Line numbers are 1-based including the header, so they match what an editor shows.
        assertThat(result.errors().get(0).line()).isEqualTo(3);
        assertThat(result.errors().get(0).message()).contains("phone");
        assertThat(result.errors().get(1).line()).isEqualTo(4);
        assertThat(result.errors().get(1).message()).contains("notanumber");
    }

    @Test
    void unknownColumnsAreReportedRatherThanIgnoredSilently() {
        // A typo'd header means those facts never reach the agent, and the call goes out
        // sounding confidently incomplete.
        TargetCsvParseResult result = TargetCsvImporter.parse("""
                phone,debtamout,notes
                998901234567,1500000,hello
                """);

        assertThat(result.unknownColumns()).containsExactly("debtamout", "notes");
        assertThat(result.targets().get(0).contextJson()).isEqualTo("{}");
    }

    @Test
    void blankRowsAndTrailingNewlinesAreNotErrors() {
        TargetCsvParseResult result = TargetCsvImporter.parse(
                "phone\r\n998901234567\r\n\r\n998901234568\r\n");

        assertThat(result.targets()).hasSize(2);
        assertThat(result.errors()).isEmpty();
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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phone");
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> TargetCsvImporter.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
