package uz.murodjon.robotcallv2.storage.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.storage.application.port.input.AudioStorageUseCase;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.storage.application.port.output.AudioCompressorPort;
import uz.murodjon.robotcallv2.storage.domain.entity.CompressedAudio;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Uploads finished call recordings to MinIO/S3 via {@link FileStorageService}.
 */
@Service
public class AudioStorageService implements AudioStorageUseCase {

    private static final Logger log = LoggerFactory.getLogger(AudioStorageService.class);

    private final FileStorageUseCase fileStorageUseCase;
    private final AudioStorageProperties audioStorageProperties;
    private final AudioCompressorPort audioCompressor;

    public AudioStorageService(FileStorageUseCase fileStorageUseCase,
                               AudioStorageProperties audioStorageProperties,
                               AudioCompressorPort audioCompressor) {
        this.fileStorageUseCase = fileStorageUseCase;
        this.audioStorageProperties = audioStorageProperties;
        this.audioCompressor = audioCompressor;
    }

    @Override
    public StoredFile upload(Path file, long companyId, String originalName) {
        CompressedAudio compressed = audioCompressor.compress(file, originalName);
        try {
            return fileStorageUseCase.store(compressed.path(), companyId, FileCategory.AUDIO,
                    compressed.originalName(), compressed.contentType());
        } finally {
            if (compressed.isTemporary()) {
                try {
                    Files.deleteIfExists(compressed.path());
                } catch (Exception e) {
                    log.warn("Could not delete temporary compressed recording {}: {}", compressed.path(), e.getMessage());
                }
            }
        }
    }

    @Override
    public void deleteLocalCopy(Path file) {
        if (!audioStorageProperties.deleteLocalAfterUpload() || file == null) {
            return;
        }
        try {
            if (Files.deleteIfExists(file)) {
                log.debug("Deleted local copy of {}", file);
            }
        } catch (Exception e) {
            log.warn("Could not delete local recording {}: {}", file, e.getMessage());
        }
    }
}
