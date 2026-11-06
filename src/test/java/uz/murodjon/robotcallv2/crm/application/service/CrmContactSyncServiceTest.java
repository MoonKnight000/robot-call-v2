package uz.murodjon.robotcallv2.crm.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactCrmImportUseCase;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactFieldUseCase;
import uz.murodjon.robotcallv2.contact.domain.entity.ImportedCrmContact;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactImportOutcome;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactSourcePort;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactSyncRepository;
import uz.murodjon.robotcallv2.crm.domain.entity.*;
import uz.murodjon.robotcallv2.crm.domain.enums.CrmContactSyncStatus;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmContactSyncServiceTest {

    private static final CrmConnection BITRIX = new CrmConnection(CrmProvider.BITRIX24, "https://acme.bitrix24.kz/rest/1/x/", null);

    private CrmContactSyncRepository repository;
    private CrmContactSourcePort source;
    private CrmConnectionResolver resolver;
    private ContactCrmImportUseCase importUseCase;
    private CrmContactSyncService service;

    @BeforeEach
    void setUp() {
        repository = mock(CrmContactSyncRepository.class);
        source = mock(CrmContactSourcePort.class);
        resolver = mock(CrmConnectionResolver.class);
        importUseCase = mock(ContactCrmImportUseCase.class);
        when(source.isSupported(CrmProvider.BITRIX24)).thenReturn(true);
        service = new CrmContactSyncService(repository, List.of(source), resolver, importUseCase,
                mock(ContactFieldUseCase.class));
        when(repository.findSettings(1L)).thenReturn(new CrmContactSyncSettings(1L, true,
                List.of(new CrmContactFieldMapping("UF_CRM_1", "lead_status")), List.of(), Instant.now()));
        when(resolver.findConnection(1L)).thenReturn(BITRIX);
    }

    private CrmContactSyncState lastSavedState() {
        ArgumentCaptor<CrmContactSyncState> captor = ArgumentCaptor.forClass(CrmContactSyncState.class);
        verify(repository, atLeastOnce()).saveState(eq(1L), captor.capture());
        return captor.getValue();
    }

    @Test
    void importsEveryPageAndAdvancesTheCursor() {
        Instant cursor = Instant.parse("2026-11-01T00:00:00Z");
        when(repository.findState(1L)).thenReturn(new CrmContactSyncState("BITRIX24", cursor, null, null, null, null, null, null));
        Instant t1 = Instant.parse("2026-11-02T00:00:00Z");
        Instant t2 = Instant.parse("2026-11-03T00:00:00Z");
        when(source.findContactsModifiedSince(BITRIX, cursor, null, Set.of("UF_CRM_1"))).thenReturn(new CrmContactPage(
                List.of(new CrmContactRecord("1", "Aziz", List.of("+998901234567"), Map.of("UF_CRM_1", "hot"), t1)), "50"));
        when(source.findContactsModifiedSince(BITRIX, cursor, "50", Set.of("UF_CRM_1"))).thenReturn(CrmContactPage.last(
                List.of(new CrmContactRecord("2", "Ali", List.of(), Map.of(), t2))));
        when(importUseCase.importCrmContact(eq(1L), any())).thenReturn(ContactImportOutcome.CREATED);

        service.runContactSync(1L);

        ArgumentCaptor<ImportedCrmContact> imported = ArgumentCaptor.forClass(ImportedCrmContact.class);
        verify(importUseCase).importCrmContact(eq(1L), imported.capture());
        assertThat(imported.getValue().customFields()).containsEntry("lead_status", "hot");
        CrmContactSyncState state = lastSavedState();
        assertThat(state.lastStatus()).isEqualTo(CrmContactSyncStatus.SUCCESS);
        assertThat(state.cursor()).isEqualTo(t2);
        assertThat(state.lastCounts().created()).isEqualTo(1);
        assertThat(state.lastCounts().skipped()).isEqualTo(1);
        assertThat(state.nextRunAt()).isAfter(Instant.now());
    }

    @Test
    void openPassGoesOnFromItsPage() {
        Instant cursor = Instant.parse("2026-11-05T00:00:00Z");
        Instant passSince = Instant.parse("2026-11-01T00:00:00Z");
        when(repository.findState(1L)).thenReturn(new CrmContactSyncState("BITRIX24", cursor,
                new CrmContactSyncResume(passSince, "100"), null, null, null, null, null));
        when(source.findContactsModifiedSince(BITRIX, passSince, "100", Set.of("UF_CRM_1")))
                .thenReturn(CrmContactPage.last(List.of()));

        service.runContactSync(1L);

        CrmContactSyncState state = lastSavedState();
        assertThat(state.resume()).isNull();
        assertThat(state.cursor()).isEqualTo(cursor);
    }

    @Test
    void failedPageStaysOpenForTheNextRun() {
        when(repository.findState(1L)).thenReturn(CrmContactSyncState.empty());
        when(source.findContactsModifiedSince(BITRIX, null, null, Set.of("UF_CRM_1"))).thenReturn(new CrmContactPage(
                List.of(new CrmContactRecord("1", "Aziz", List.of(), Map.of(), Instant.parse("2026-11-02T00:00:00Z"))), "50"));
        when(source.findContactsModifiedSince(BITRIX, null, "50", Set.of("UF_CRM_1")))
                .thenThrow(new ExternalServiceException(ErrorCode.CRM_CONTACT_SYNC_FAILED, "uysot", "HTTP 429"));

        service.runContactSync(1L);

        CrmContactSyncState state = lastSavedState();
        assertThat(state.lastStatus()).isEqualTo(CrmContactSyncStatus.FAILED);
        assertThat(state.resume()).isEqualTo(new CrmContactSyncResume(null, "50"));
    }

    @Test
    void switchedCrmIsReadInFull() {
        when(repository.findState(1L)).thenReturn(new CrmContactSyncState("AMOCRM", Instant.now(), null, null, null, null, null, null));
        when(source.findContactsModifiedSince(eq(BITRIX), isNull(), isNull(), any())).thenReturn(CrmContactPage.last(List.of()));

        service.runContactSync(1L);

        verify(source).findContactsModifiedSince(eq(BITRIX), isNull(), isNull(), any());
    }

    @Test
    void crmFailureIsRecorded() {
        when(repository.findState(1L)).thenReturn(CrmContactSyncState.empty());
        when(source.findContactsModifiedSince(any(), any(), any(), any()))
                .thenThrow(new ExternalServiceException(ErrorCode.CRM_CONTACT_SYNC_FAILED, "bitrix24", "HTTP 401"));

        service.runContactSync(1L);

        CrmContactSyncState state = lastSavedState();
        assertThat(state.lastStatus()).isEqualTo(CrmContactSyncStatus.FAILED);
        assertThat(state.lastError()).contains("HTTP 401");
    }

    @Test
    void noConnectionIsRecorded() {
        when(repository.findState(1L)).thenReturn(CrmContactSyncState.empty());
        when(resolver.findConnection(1L)).thenReturn(null);

        service.runContactSync(1L);

        assertThat(lastSavedState().lastStatus()).isEqualTo(CrmContactSyncStatus.NOT_CONNECTED);
        verifyNoInteractions(importUseCase);
    }
}
