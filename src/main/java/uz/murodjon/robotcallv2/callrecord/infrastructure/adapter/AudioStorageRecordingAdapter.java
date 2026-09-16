package uz.murodjon.robotcallv2.callrecord.infrastructure.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.application.port.output.RecordingStoragePort;
import uz.murodjon.robotcallv2.storage.application.port.input.AudioStorageUseCase;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

import java.nio.file.Path;

/**
 * Keeps call recordings in the platform's file storage. The call records reach storage only
 * through here, so storage's retention sweep — which asks the call records which recordings
 * have expired — is the one direction left between the two features' services.
 */
@Component
public class AudioStorageRecordingAdapter implements RecordingStoragePort {

    private final AudioStorageUseCase audioStorageService;

    public AudioStorageRecordingAdapter(AudioStorageUseCase audioStorageService) {
        this.audioStorageService = audioStorageService;
    }

    @Override
    public Long uploadRecording(Path wav, long companyId, String originalName) {
        StoredFile stored = audioStorageService.upload(wav, companyId, originalName);
        return stored != null ? stored.id() : null;
    }

    @Override
    public void deleteLocalCopy(Path wav) {
        audioStorageService.deleteLocalCopy(wav);
    }
}
