package uz.murodjon.robotcallv2.campaign.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CampaignTargetExportServiceTest {

    @Test
    void leavesPlainValuesAndPhonesUntouched() {
        assertEquals("uz-UZ", CampaignTargetExportService.escape("uz-UZ"));
        assertEquals("+998901234567", CampaignTargetExportService.escape("+998901234567"));
        assertEquals("", CampaignTargetExportService.escape(null));
    }

    @Test
    void quotesCellsWithSeparatorsAndDoublesQuotes() {
        assertEquals("\"Aziz, Karimov\"", CampaignTargetExportService.escape("Aziz, Karimov"));
        assertEquals("\"say \"\"hi\"\"\"", CampaignTargetExportService.escape("say \"hi\""));
    }

    @Test
    void defusesValuesASpreadsheetWouldRunAsFormula() {
        assertEquals("'=SUM(A1:A2)", CampaignTargetExportService.escape("=SUM(A1:A2)"));
        assertEquals("'@cmd", CampaignTargetExportService.escape("@cmd"));
        assertEquals("'+abc", CampaignTargetExportService.escape("+abc"));
    }
}
