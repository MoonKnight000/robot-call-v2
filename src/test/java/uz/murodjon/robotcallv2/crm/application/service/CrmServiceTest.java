package uz.murodjon.robotcallv2.crm.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmSettingsUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.company.CompanyFixtures;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateLeadStageRequest;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmConnectorPort;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final String PHONE = "+998901234567";
    private static final String NOTE = "Mijoz ertaga to'lashini aytdi.";

    private CrmIntegrationUseCase integrations;
    private CrmConnectorPort uysot;
    private CrmConnectorPort amoCrm;
    private CrmConnectorPort bitrix24;

    @BeforeEach
    void setUp() {
        integrations = mock(CrmIntegrationUseCase.class);
        uysot = connectorFor(CrmProvider.UYSOT);
        amoCrm = connectorFor(CrmProvider.AMOCRM, CrmProvider.KOMMO);
        bitrix24 = connectorFor(CrmProvider.BITRIX24);
    }

    @Test
    void syncLeadStageUpdatesMeetingBookedStatus() {
        CrmProperties props = new CrmProperties(true, "https://uysot.example", "platform-token",
                "/lead/{id}", "/lead/filter", "/lead/{id}/note", "/contract/{id}", "/contract/filter",
                "/call-history", 3, "/v1/open-api/task", "/v1/open-api/lead/{id}/status",
                201, 202, 203, 204);
        CrmService service = client(props);
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.empty());
        when(uysot.updateLeadStage(any(), eq(new UpdateLeadStageRequest(42L, 201, "Sotuv ofisiga uchrashuv belgilandi")))).thenReturn(true);

        CallSummary summary = new CallSummary("Uchrashuv belgilandi", Map.of("meetingBooked", true), null, false, null, null);
        boolean synced = service.syncLeadStage(COMPANY_ID, 42L, summary, Disposition.GOAL_REACHED);

        assertTrue(synced);
        verify(uysot).updateLeadStage(any(), eq(new UpdateLeadStageRequest(42L, 201, "Sotuv ofisiga uchrashuv belgilandi")));
    }

    @Test
    void syncLeadStageUsesCompanyDispositionStatusMapping() {
        CrmSettingsUseCase settings = mock(CrmSettingsUseCase.class);
        when(settings.findByCompanyId(COMPANY_ID)).thenReturn(new CrmSettings(COMPANY_ID, null, null,
                Map.of("FAILED", 505), null));
        CrmService service = new CrmService(uysotProperties(), integrations, List.of(uysot),
                CompanyFixtures.openCompanyUseCase(), settings);
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.empty());
        when(uysot.updateLeadStage(any(), eq(new UpdateLeadStageRequest(42L, 505, "Qo'ng'iroq natijasi (FAILED)")))).thenReturn(true);

        CallSummary summary = new CallSummary("Qo'ng'iroq uzilib qoldi", Map.of(), null, false, null, null);
        boolean synced = service.syncLeadStage(COMPANY_ID, 42L, summary, Disposition.FAILED);

        assertTrue(synced);
        verify(uysot).updateLeadStage(any(), eq(new UpdateLeadStageRequest(42L, 505, "Qo'ng'iroq natijasi (FAILED)")));
    }

    @Test
    void composeNoteTextAppendsAudioUrlWhenProvided() {
        CallSummary summary = new CallSummary(NOTE, Map.of(), null, false, null, null);
        String textWithAudio = client(uysotProperties()).composeNoteText(summary, 54321L);

        assertTrue(textWithAudio.contains("Audio yozuv: /api/files/54321"));
    }

    @Test
    void amoCrmCompanyIsServedByTheAmoCrmConnector() {
        CrmConnection connection = new CrmConnection(CrmProvider.AMOCRM, "https://acme.amocrm.ru", "token");
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.of(connection));
        CrmClientSnapshot snapshot = new CrmClientSnapshot("Ali", null, null, null, null, null, null, null);
        when(amoCrm.findByPhone(connection, PHONE)).thenReturn(snapshot);

        assertSame(snapshot, client(uysotProperties()).findByPhone(COMPANY_ID, PHONE));
        verify(uysot, never()).findByPhone(any(), any());
        verify(bitrix24, never()).findByPhone(any(), any());
    }

    @Test
    void kommoCompanyIsServedByTheAmoCrmConnector() {
        CrmConnection connection = new CrmConnection(CrmProvider.KOMMO, "https://acme.kommo.com", "token");
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.of(connection));
        when(amoCrm.postNote(connection, 42L, NOTE)).thenReturn("1001");

        assertEquals("1001", client(uysotProperties()).postNote(COMPANY_ID, 42L, NOTE));
        verify(uysot, never()).postNote(any(), anyLong(), any());
    }

    @Test
    void bitrix24CompanyIsServedByTheBitrix24Connector() {
        CrmConnection connection = new CrmConnection(CrmProvider.BITRIX24, "https://acme.bitrix24.kz/rest/1/code", null);
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.of(connection));
        when(bitrix24.postNote(connection, 42L, NOTE)).thenReturn("991");

        assertEquals("991", client(uysotProperties()).postNote(COMPANY_ID, 42L, NOTE));
        verify(uysot, never()).postNote(any(), anyLong(), any());
        verify(amoCrm, never()).postNote(any(), anyLong(), any());
    }

    @Test
    void companyWithoutItsOwnCrmStaysOnUysotWithThePlatformToken() {
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.empty());

        client(uysotProperties()).findByPhone(COMPANY_ID, PHONE);

        verify(uysot).findByPhone(new CrmConnection(CrmProvider.UYSOT, "https://uysot.example", "platform-token"),
                PHONE);
        assertTrue(client(uysotProperties()).isConnected(COMPANY_ID));
    }

    @Test
    void uysotCompanyUsesItsOwnOAuthToken() {
        when(integrations.findConnection(COMPANY_ID))
                .thenReturn(Optional.of(new CrmConnection(CrmProvider.UYSOT, null, "company-token")));

        client(uysotProperties()).fetchClient(COMPANY_ID, 5L);

        verify(uysot).findLead(new CrmConnection(CrmProvider.UYSOT, "https://uysot.example", "company-token"), 5L);
    }

    @Test
    void chosenButDisconnectedCrmIsNotReplacedByUysot() {
        when(integrations.findConnection(COMPANY_ID))
                .thenReturn(Optional.of(new CrmConnection(CrmProvider.AMOCRM, null, null)));

        assertNull(client(uysotProperties()).postNote(COMPANY_ID, 42L, NOTE));
        assertFalse(client(uysotProperties()).isConnected(COMPANY_ID));
        verify(uysot, never()).postNote(any(), anyLong(), any());
        verify(amoCrm, never()).postNote(any(), anyLong(), any());
    }

    @Test
    void nothingIsCalledWhenUysotIsOffAndTheCompanyHasNoCrm() {
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.empty());
        CrmProperties uysotOff = new CrmProperties(false, "https://uysot.example", "platform-token",
                "/lead/{id}", "/lead/filter", "/lead/{id}/note", "/contract/{id}", "/contract/filter",
                "/call-history", 3);

        assertFalse(client(uysotOff).postCallHistory(COMPANY_ID, 42L, "voice-1", Instant.now(), 30,
                false, true, PHONE, null));
        assertFalse(client(uysotOff).isConnected(COMPANY_ID));
        verify(uysot, never()).postCallHistory(any(), any());
    }

    @Test
    void unreadableConnectionMeansNoCrmRatherThanAFailedCall() {
        when(integrations.findConnection(COMPANY_ID)).thenThrow(new IllegalStateException("no key matches"));

        assertNull(client(uysotProperties()).findByPhone(COMPANY_ID, PHONE));
        verify(uysot, never()).findByPhone(any(), any());
    }

    @Test
    void noteSpellsOutWhatTheSummaryFound() {
        CallSummary summary = new CallSummary(NOTE, Map.of("promisedDate", "2026-09-15"), null, true, null, null);

        assertEquals(NOTE + "\nVa'da qilingan sana: 2026-09-15\nKuzatuv kerak: ha",
                client(uysotProperties()).composeNoteText(summary));
    }

    @Test
    void nothingIsAskedForAMissingLeadNumberOrNote() {
        CrmService service = client(uysotProperties());

        assertNull(service.fetchClient(COMPANY_ID, null));
        assertNull(service.fetchClient(COMPANY_ID, 0L));
        assertNull(service.findByPhone(COMPANY_ID, " "));
        assertNull(service.postNote(COMPANY_ID, 0L, NOTE));
        assertNull(service.postNote(COMPANY_ID, 42L, null));
        assertFalse(service.postCallHistory(COMPANY_ID, 42L, null, Instant.now(), 30, false, true, PHONE, null));
        verifyNoInteractions(integrations);
    }

    @Test
    void companyWithNowhereToSendFindsNoClient() {
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.empty());
        CrmService service = client(uysotOff());

        assertNull(service.fetchClient(COMPANY_ID, 5L));
        assertNull(service.findByPhone(COMPANY_ID, PHONE));
        verify(uysot, never()).findLead(any(), anyLong());
        verify(uysot, never()).findByPhone(any(), any());
    }

    @Test
    void providerNoConnectorSupportsLeavesTheCompanyUnconnected() {
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.of(
                new CrmConnection(CrmProvider.BITRIX24, "https://acme.bitrix24.kz/rest/1/code", null)));
        CrmService withoutBitrix24 = new CrmService(uysotProperties(), integrations, List.of(uysot, amoCrm), CompanyFixtures.openCompanyUseCase(),
                noCrmSettings());

        assertFalse(withoutBitrix24.isConnected(COMPANY_ID));
        assertNull(withoutBitrix24.findByPhone(COMPANY_ID, PHONE));
        verify(uysot, never()).findByPhone(any(), any());
        verify(amoCrm, never()).findByPhone(any(), any());
    }

    /** The outbox re-posts on null — a connector that throws must read as "not taken", not fail the delivery. */
    @Test
    void connectorThatThrowsOnANoteReadsAsNotTaken() {
        CrmConnection connection = new CrmConnection(CrmProvider.AMOCRM, "https://acme.amocrm.ru", "token");
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.of(connection));
        when(amoCrm.postNote(connection, 42L, NOTE)).thenThrow(new IllegalStateException("socket closed"));

        assertNull(client(uysotProperties()).postNote(COMPANY_ID, 42L, NOTE));
    }

    @Test
    void callHistoryReachesTheConnectorAsOneEntry() {
        CrmConnection connection = new CrmConnection(CrmProvider.AMOCRM, "https://acme.amocrm.ru", "token");
        when(integrations.findConnection(COMPANY_ID)).thenReturn(Optional.of(connection));
        Instant startedAt = Instant.parse("2026-09-14T09:58:00Z");
        when(amoCrm.postCallHistory(connection,
                new CrmCallHistoryEntry(42L, "voice-1", startedAt, 95, true, false, PHONE, null))).thenReturn(true);

        assertTrue(client(uysotProperties()).postCallHistory(COMPANY_ID, 42L, "voice-1", startedAt, 95,
                true, false, PHONE, null));
    }

    @Test
    void noteSpellsOutEveryPartOfTheSummaryInOrder() {
        CallSummary summary = new CallSummary("  " + NOTE + "  ", Map.of("reasonCode", "NO_MONEY",
                "promisedAmount", 1500000, "promisedDate", "2026-09-15"), Sentiment.NEGATIVE, true,
                "Maoshdan keyin", "2026-09-15T10:00:00", 85, 40);

        assertEquals(NOTE
                        + "\nSabab: NO_MONEY"
                        + "\nVa'da qilingan summa: 1500000"
                        + "\nVa'da qilingan sana: 2026-09-15"
                        + "\nKayfiyat: NEGATIVE"
                        + "\nQayta qo'ng'iroq: 2026-09-15T10:00:00"
                        + "\nKuzatuv kerak: Maoshdan keyin"
                        + "\nSuhbat sifati: 85/100",
                client(uysotProperties()).composeNoteText(summary));
    }

    @Test
    void summaryWithNothingInItStillLeavesANote() {
        CallSummary empty = new CallSummary(" ", Map.of(), null, false, null, null, null, null);

        assertEquals("AI operator bilan suhbat bo'lib o'tdi.", client(uysotProperties()).composeNoteText(empty));
    }

    @Test
    void runawayNoteIsCutToFourThousandCharacters() {
        CallSummary runaway = new CallSummary("a".repeat(5000), Map.of(), null, false, null, null, null, null);

        assertEquals("a".repeat(4000), client(uysotProperties()).composeNoteText(runaway));
    }

    private CrmService client(CrmProperties crmProperties) {
        return new CrmService(crmProperties, integrations, List.of(uysot, amoCrm, bitrix24), CompanyFixtures.openCompanyUseCase(),
                noCrmSettings());
    }

    private static CrmConnectorPort connectorFor(CrmProvider... providers) {
        CrmConnectorPort connector = mock(CrmConnectorPort.class);
        for (CrmProvider provider : providers) {
            when(connector.isSupported(provider)).thenReturn(true);
        }
        return connector;
    }

    private static CrmProperties uysotProperties() {
        return new CrmProperties(true, "https://uysot.example", "platform-token",
                "/lead/{id}", "/lead/filter", "/lead/{id}/note", "/contract/{id}", "/contract/filter",
                "/call-history", 3);
    }

    private static CrmProperties uysotOff() {
        return new CrmProperties(false, "https://uysot.example", "platform-token",
                "/lead/{id}", "/lead/filter", "/lead/{id}/note", "/contract/{id}", "/contract/filter",
                "/call-history", 3);
    }

    /** A company that saved no CRM settings of its own: every platform default applies. */
    private static CrmSettingsUseCase noCrmSettings() {
        CrmSettingsUseCase settings = mock(CrmSettingsUseCase.class);
        when(settings.findByCompanyId(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> new CrmSettings(invocation.getArgument(0), null, null, null, null, null, null, null, null));
        return settings;
    }
}
