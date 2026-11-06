package uz.murodjon.robotcallv2.crm.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactCrmPushUseCase;
import uz.murodjon.robotcallv2.contact.domain.entity.PendingCrmPush;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactSyncRepository;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactWriterPort;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactFieldMapping;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncSettings;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmContactPushServiceTest {

    private static final CrmConnection BITRIX = new CrmConnection(CrmProvider.BITRIX24, "https://acme.bitrix24.kz/rest/1/x/", null);
    private static final PendingCrmPush PUSH = new PendingCrmPush(1L, 7L, "BITRIX24", "42", Instant.now(), 0,
            Map.of("lead_status", "issiq", "email", ""));

    private ContactCrmPushUseCase pushUseCase;
    private CrmContactSyncRepository repository;
    private CrmConnectionResolver resolver;
    private CrmContactWriterPort writer;
    private CrmContactPushService service;

    @BeforeEach
    void setUp() {
        pushUseCase = mock(ContactCrmPushUseCase.class);
        repository = mock(CrmContactSyncRepository.class);
        resolver = mock(CrmConnectionResolver.class);
        writer = mock(CrmContactWriterPort.class);
        when(writer.isSupported(CrmProvider.BITRIX24)).thenReturn(true);
        service = new CrmContactPushService(pushUseCase, repository, resolver, List.of(writer));
        when(repository.findSettings(1L)).thenReturn(new CrmContactSyncSettings(1L, false, List.of(),
                List.of(new CrmContactFieldMapping("UF_CRM_1", "lead_status"),
                        new CrmContactFieldMapping("EMAIL", "email")), Instant.now()));
        when(resolver.findConnection(1L)).thenReturn(BITRIX);
    }

    @Test
    void writesMappedNonEmptyValuesEvenWithTheImportOff() {
        assertThat(service.push(PUSH)).isNull();

        verify(writer).updateContactFields(BITRIX, "42", Map.of("UF_CRM_1", "issiq"));
    }

    @Test
    void lettingGoWhenTheCrmChangedOrNothingIsMapped() {
        when(resolver.findConnection(1L)).thenReturn(new CrmConnection(CrmProvider.AMOCRM, "https://a.amocrm.ru", "t"));
        assertThat(service.push(PUSH)).isNull();

        when(resolver.findConnection(1L)).thenReturn(BITRIX);
        when(repository.findSettings(1L)).thenReturn(null);
        assertThat(service.push(PUSH)).isNull();

        verify(writer, never()).updateContactFields(any(), any(), any());
    }

    @Test
    void aFailedWriteReturnsItsReason() {
        doThrow(new ExternalServiceException(ErrorCode.CRM_CONTACT_PUSH_FAILED, "bitrix24", "HTTP 503"))
                .when(writer).updateContactFields(any(), any(), any());

        assertThat(service.push(PUSH)).contains("HTTP 503");
    }

    @Test
    void runFinishesEveryDuePush() {
        when(pushUseCase.findDueCrmPushes(any(), eq(5))).thenReturn(List.of(PUSH));

        service.runDuePushes(5);

        verify(pushUseCase).finishCrmPush(PUSH, null);
    }
}
