package uz.murodjon.robotcallv2.voicemail.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.voicemail.application.dto.SaveVoicemailBoxRequest;
import uz.murodjon.robotcallv2.voicemail.application.port.output.VoicemailBoxRepository;
import uz.murodjon.robotcallv2.voicemail.application.port.output.VoicemailMessageRepository;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoicemailServiceTest {

    private static final long COMPANY = 7L;

    private VoicemailBoxRepository boxRepository;
    private VoicemailMessageRepository messageRepository;
    private FileStorageUseCase fileStorageService;
    private NotificationUseCase notificationService;
    private ContactCenterEventUseCase contactCenterEventUseCase;
    private VoicemailService service;

    @BeforeEach
    void setUp() {
        boxRepository = mock(VoicemailBoxRepository.class);
        messageRepository = mock(VoicemailMessageRepository.class);
        fileStorageService = mock(FileStorageUseCase.class);
        notificationService = mock(NotificationUseCase.class);
        contactCenterEventUseCase = mock(ContactCenterEventUseCase.class);
        service = new VoicemailService(boxRepository, messageRepository, fileStorageService, notificationService,
                contactCenterEventUseCase, mock(AuditUseCase.class));
    }

    @Test
    void createBoxFillsTheDefaults() {
        when(boxRepository.create(eq(COMPANY), any())).thenReturn(3L);
        when(boxRepository.find(COMPANY, 3L)).thenReturn(box(3L));

        service.createBox(COMPANY, new SaveVoicemailBoxRequest("  Sales  ", "  ", null, null, null, null));

        verify(boxRepository).create(eq(COMPANY), argThat(box -> box.name().equals("Sales")
                && box.greetingText() == null && box.maxSeconds() == 120 && box.transcribe() && box.enabled()));
    }

    @Test
    void createBoxRejectsAMaxLengthOutOfRange() {
        assertThatThrownBy(() -> service.createBox(COMPANY,
                new SaveVoicemailBoxRequest("Sales", null, null, 5, null, null)))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VOICEMAIL_BOX_MAX_SECONDS_OUT_OF_RANGE));
        verify(boxRepository, never()).create(anyLong(), any());
    }

    @Test
    void updateBoxFailsForAnotherCompanysBox() {
        assertThatThrownBy(() -> service.updateBox(COMPANY, 3L,
                new SaveVoicemailBoxRequest("Sales", null, null, null, null, null)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VOICEMAIL_BOX_NOT_FOUND));
    }

    @Test
    void downloadAudioFailsWhenTheMessageHasNoAudio() {
        when(messageRepository.find(COMPANY, 1L)).thenReturn(message(1L, "998901234567", null, null));

        assertThatThrownBy(() -> service.downloadAudio(COMPANY, 1L))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VOICEMAIL_AUDIO_NOT_FOUND));
    }

    @Test
    void recordMessageStoresTheAudioAndTellsTheOperators() {
        when(fileStorageService.store(any(byte[].class), eq(COMPANY), eq(FileCategory.AUDIO), eq("voicemail-11.wav"),
                eq("audio/wav"))).thenReturn(storedFile(50L));
        when(messageRepository.create(eq(COMPANY), any())).thenReturn(1L);
        VoicemailMessage saved = message(1L, "998901234567", 50L, "Qayta qo'ng'iroq qiling");
        when(messageRepository.find(COMPANY, 1L)).thenReturn(saved);

        service.recordMessage(COMPANY, 3L, 11L, "998901234567", new byte[]{1}, 8, "Qayta qo'ng'iroq qiling");

        verify(messageRepository).create(eq(COMPANY), argThat(m -> m.storedFileId() == 50L
                && m.status() == VoicemailStatus.NEW));
        verify(notificationService).notify(eq(COMPANY), eq(NotificationType.VOICEMAIL_RECEIVED), anyString(),
                eq("998901234567: Qayta qo'ng'iroq qiling"), isNull());
        verify(contactCenterEventUseCase).publishToCompany(COMPANY, ContactCenterEventType.VOICEMAIL, saved);
    }

    /** A message without its audio is worth more than no message. */
    @Test
    void recordMessageKeepsTheMessageWhenStorageIsDown() {
        when(fileStorageService.store(any(byte[].class), anyLong(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("minio down"));
        when(messageRepository.create(eq(COMPANY), any())).thenReturn(1L);
        when(messageRepository.find(COMPANY, 1L)).thenReturn(message(1L, "998901234567", null, null));

        service.recordMessage(COMPANY, 3L, 11L, "998901234567", new byte[]{1}, 8, "  ");

        verify(messageRepository).create(eq(COMPANY), argThat(m -> m.storedFileId() == null && m.transcript() == null));
        verify(notificationService).notify(eq(COMPANY), eq(NotificationType.VOICEMAIL_RECEIVED), anyString(),
                eq("998901234567 — 8 soniyalik xabar"), isNull());
    }

    @Test
    void theNotificationCutsALongTranscript() {
        String transcript = "a".repeat(300);
        when(messageRepository.create(eq(COMPANY), any())).thenReturn(1L);
        when(messageRepository.find(COMPANY, 1L)).thenReturn(message(1L, null, null, transcript));

        service.recordMessage(COMPANY, 3L, null, null, new byte[]{1}, 8, transcript);

        verify(notificationService).notify(eq(COMPANY), eq(NotificationType.VOICEMAIL_RECEIVED), anyString(),
                eq("noma'lum raqam: " + "a".repeat(200) + "…"), isNull());
    }

    private static VoicemailBox box(long id) {
        return new VoicemailBox(id, COMPANY, "Sales", null, null, 120, true, true, null);
    }

    private static VoicemailMessage message(long id, String phone, Long storedFileId, String transcript) {
        return new VoicemailMessage(id, COMPANY, 3L, 11L, phone, storedFileId, 8, transcript, VoicemailStatus.NEW, null);
    }

    private static StoredFile storedFile(long id) {
        return new StoredFile(id, COMPANY, FileCategory.AUDIO, "voicemail-11.wav", "p", "b", "wav", 1, null);
    }
}
