package uz.murodjon.robotcallv2.voice.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.voice.application.dto.CloneVoiceRequest;
import uz.murodjon.robotcallv2.voice.application.port.output.TtsVoiceRepository;
import uz.murodjon.robotcallv2.voice.application.port.output.VoiceClonerPort;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoiceCloneServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final UploadedFile SAMPLE = new UploadedFile("dilnoza.mp3", "audio/mpeg", 3, new byte[]{1, 2, 3});

    private TtsVoiceRepository repository;
    private VoiceClonerPort cloner;
    private VoiceCloneService service;

    @BeforeEach
    void setUp() {
        repository = mock(TtsVoiceRepository.class);
        cloner = mock(VoiceClonerPort.class);
        when(cloner.isAvailable()).thenReturn(true);
        when(cloner.findProviderName()).thenReturn("elevenlabs");
        service = new VoiceCloneService(repository, cloner, mock(AuditUseCase.class));
    }

    @Test
    void aClonedVoiceBelongsToTheCompanyThatMadeIt() {
        when(cloner.createClone(anyString(), anyList())).thenReturn("AbC123xyz");

        TtsVoice voice = service.cloneVoice(COMPANY_ID, new CloneVoiceRequest("Dilnoza", "uz-UZ", true), List.of(SAMPLE));

        assertThat(voice.id()).isEqualTo("clone-7-abc123xyz");
        assertThat(voice.name()).isEqualTo("AbC123xyz");
        assertThat(voice.companyId()).isEqualTo(COMPANY_ID);
        assertThat(voice.isVisibleTo(COMPANY_ID)).isTrue();
        assertThat(voice.isVisibleTo(8L)).isFalse();
        verify(repository).create(voice);
    }

    @Test
    void noVoiceIsClonedWithoutTheSpeakersConsent() {
        assertRefused(() -> service.cloneVoice(COMPANY_ID, new CloneVoiceRequest("Dilnoza", "uz-UZ", false), List.of(SAMPLE)),
                ErrorCode.VOICE_CLONE_CONSENT_REQUIRED);
        verifyNoInteractions(cloner);
    }

    @Test
    void aNonAudioFileIsRefused() {
        UploadedFile pdf = new UploadedFile("cv.pdf", "application/pdf", 3, new byte[]{1, 2, 3});
        assertRefused(() -> service.cloneVoice(COMPANY_ID, new CloneVoiceRequest("Dilnoza", "uz-UZ", true), List.of(pdf)),
                ErrorCode.VOICE_CLONE_SAMPLE_NOT_AUDIO);
    }

    @Test
    void theCompanyLimitStopsANewClone() {
        when(repository.countActiveByCompanyId(COMPANY_ID)).thenReturn((long) VoiceCloneService.MAX_CLONES_PER_COMPANY);
        assertRefused(() -> service.cloneVoice(COMPANY_ID, new CloneVoiceRequest("Dilnoza", "uz-UZ", true), List.of(SAMPLE)),
                ErrorCode.VOICE_CLONE_LIMIT_REACHED);
        verify(cloner, never()).createClone(anyString(), anyList());
    }

    @Test
    void anotherCompanysCloneCannotBeDeleted() {
        when(repository.findActiveById("clone-8-x")).thenReturn(
                new TtsVoice("clone-8-x", "elevenlabs", "uz-UZ", "x", "X", null, null, null, 8L));
        assertRefused(() -> service.deleteClonedVoice(COMPANY_ID, "clone-8-x"), ErrorCode.TTS_VOICE_NOT_FOUND);
        verify(cloner, never()).deleteClone(any());
    }

    @Test
    void aSharedVoiceCannotBeDeleted() {
        when(repository.findActiveById("nigora")).thenReturn(
                new TtsVoice("nigora", "yandex", "uz-UZ", "nigora", "Nigora", null, null, null, null));
        assertRefused(() -> service.deleteClonedVoice(COMPANY_ID, "nigora"), ErrorCode.TTS_VOICE_NOT_FOUND);
    }

    @Test
    void deletingAnOwnCloneRemovesItAtTheVendorAndSwitchesItOff() {
        when(repository.findActiveById("clone-7-abc")).thenReturn(
                new TtsVoice("clone-7-abc", "elevenlabs", "uz-UZ", "AbC", "Dilnoza", null, null, null, COMPANY_ID));

        service.deleteClonedVoice(COMPANY_ID, "clone-7-abc");

        verify(cloner).deleteClone("AbC");
        verify(repository).deactivate("clone-7-abc");
    }

    private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
