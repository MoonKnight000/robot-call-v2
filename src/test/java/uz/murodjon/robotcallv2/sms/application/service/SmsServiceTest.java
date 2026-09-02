package uz.murodjon.robotcallv2.sms.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.output.ExternalSmsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SmsServiceTest {

    private ExternalSmsClient externalSmsClient;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        externalSmsClient = mock(ExternalSmsClient.class);
        auditService = mock(AuditService.class);
    }

    @Test
    void sendsSmsSuccessfullyWhenEnabled() {
        SmsService service = new SmsService(true, externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("+998901234567", "To'lov linki: https://pay.uz/123");

        when(externalSmsClient.send("+998901234567", "To'lov linki: https://pay.uz/123")).thenReturn(true);

        boolean result = service.sendSms(req);

        assertThat(result).isTrue();
        verify(externalSmsClient).send("+998901234567", "To'lov linki: https://pay.uz/123");
        verify(auditService).record("SMS_SENT", "sms", "+998901234567", "To'lov linki: https://pay.uz/123");
    }

    @Test
    void returnsTrueWithoutCallingClientWhenDisabled() {
        SmsService service = new SmsService(false, externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("+998901234567", "Test message");

        boolean result = service.sendSms(req);

        assertThat(result).isTrue();
        assertThat(service.isEnabled()).isFalse();
        verifyNoInteractions(externalSmsClient);
        verifyNoInteractions(auditService);
    }

    @Test
    void returnsFalseWhenExternalClientFails() {
        SmsService service = new SmsService(true, externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("+998901234567", "Test message");

        when(externalSmsClient.send(anyString(), anyString())).thenThrow(new RuntimeException("SMS Gateway Timeout"));

        boolean result = service.sendSms(req);

        assertThat(result).isFalse();
        verifyNoInteractions(auditService);
    }

    @Test
    void throwsValidationExceptionWhenRequestHasBlankFields() {
        SmsService service = new SmsService(true, externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("", "Test message");

        assertThatThrownBy(() -> service.sendSms(req))
                .isInstanceOf(ValidationException.class);
    }
}
