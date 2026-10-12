package uz.murodjon.robotcallv2.notification.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.notification.application.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSettingsRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class NotificationSettingsServiceTest {

    private static final long COMPANY = 7L;

    private NotificationSettingsRepository repository;
    private NotificationSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationSettingsRepository.class);
        service = new NotificationSettingsService(repository, mock(AuditUseCase.class));
    }

    @Test
    void anEnabledChannelNeedsAValidTarget() {
        assertRejected(new NotificationChannel(NotificationChannelType.EMAIL, "not-an-email", true));
    }

    /** A target typed in but left disabled is still checked — it will be enabled one day. */
    @Test
    void aDisabledChannelWithATargetIsCheckedToo() {
        assertRejected(new NotificationChannel(NotificationChannelType.EMAIL, "not-an-email", false));
    }

    /** The server POSTs to a webhook, so an internal address would make it a request forger. */
    @Test
    void aWebhookToAnInternalAddressIsRejected() {
        assertRejected(new NotificationChannel(NotificationChannelType.WEBHOOK, "http://127.0.0.1:8088/ari", true));
    }

    @Test
    void aDisabledChannelWithoutATargetIsSaved() {
        List<NotificationChannel> channels = List.of(
                new NotificationChannel(NotificationChannelType.TELEGRAM, " ", false),
                new NotificationChannel(NotificationChannelType.EMAIL, "ops@example.com", true));
        when(repository.save(COMPANY, channels, List.of())).thenReturn(new NotificationSettings(channels, List.of()));

        assertThat(service.updateByCompanyId(COMPANY, new UpdateNotificationSettingsRequest(channels, List.of()))
                .channels()).hasSize(2);
    }

    private void assertRejected(NotificationChannel channel) {
        assertThatThrownBy(() -> service.updateByCompanyId(COMPANY,
                new UpdateNotificationSettingsRequest(List.of(channel), List.of())))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_TARGET_INVALID));
        verify(repository, never()).save(anyLong(), any(), any());
    }
}
