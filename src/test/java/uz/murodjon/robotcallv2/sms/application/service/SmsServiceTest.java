package uz.murodjon.robotcallv2.sms.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.output.SmsSenderPort;
import uz.murodjon.robotcallv2.sms.domain.entity.SmsMessage;
import uz.murodjon.robotcallv2.sms.infrastructure.config.SmsProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SmsServiceTest {

    private SmsSenderPort externalSmsClient;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        externalSmsClient = mock(SmsSenderPort.class);
        auditService = mock(AuditService.class);
    }

    @Test
    void sendsSmsSuccessfullyWhenEnabled() {
        SmsService service = new SmsService(new SmsProperties(true, null, null), externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("+998901234567", "To'lov linki: https://pay.uz/123");

        when(externalSmsClient.send(any(SmsMessage.class))).thenReturn(true);

        boolean result = service.sendSms(1L, req);

        assertThat(result).isTrue();
        verify(externalSmsClient).send(argThat(sms -> sms.getPhone().equals("+998901234567")
                && sms.getMessage().equals("To'lov linki: https://pay.uz/123")));
        // The audit trail keeps the masked number and the length, never the body itself.
        verify(auditService).record(1L, "SMS_SENT", "sms", PhoneMasker.mask("+998901234567"),
                "To'lov linki: https://pay.uz/123".length() + " chars");
    }

    @Test
    void reportsNotSentWithoutCallingClientWhenDisabled() {
        SmsService service = new SmsService(new SmsProperties(false, null, null), externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("+998901234567", "Test message");

        boolean result = service.sendSms(1L, req);

        assertThat(result).isFalse();
        assertThat(service.isEnabled()).isFalse();
        verifyNoInteractions(externalSmsClient);
        verifyNoInteractions(auditService);
    }

    @Test
    void returnsFalseWhenExternalClientFails() {
        SmsService service = new SmsService(new SmsProperties(true, null, null), externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("+998901234567", "Test message");

        when(externalSmsClient.send(any(SmsMessage.class))).thenThrow(new RuntimeException("SMS Gateway Timeout"));

        boolean result = service.sendSms(1L, req);

        assertThat(result).isFalse();
        verifyNoInteractions(auditService);
    }

    @Test
    void returnsFalseWhenTheGatewayRefuses() {
        SmsService service = new SmsService(new SmsProperties(true, null, null), externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("+998901234567", "Test message");

        when(externalSmsClient.send(any(SmsMessage.class))).thenReturn(false);

        assertThat(service.sendSms(1L, req)).isFalse();
        verifyNoInteractions(auditService);
    }

    @Test
    void throwsValidationExceptionWhenRequestHasBlankFields() {
        SmsService service = new SmsService(new SmsProperties(true, null, null), externalSmsClient, auditService);
        SmsSendRequest req = new SmsSendRequest("", "Test message");

        assertThatThrownBy(() -> service.sendSms(1L, req))
                .isInstanceOf(ValidationException.class);
    }
}
