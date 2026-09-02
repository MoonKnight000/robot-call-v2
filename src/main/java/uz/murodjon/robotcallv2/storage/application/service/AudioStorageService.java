package uz.murodjon.robotcallv2.storage.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Uploads finished call recordings to MinIO/S3 via {@link FileStorageService}.
 */
@Service
public class AudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(AudioStorageService.class);
    private static final String CONTENT_TYPE = "audio/wav";

    private final FileStorageService files;
    private final AudioStorageProperties props;

    public AudioStorageService(FileStorageService files, AudioStorageProperties props) {
        this.files = files;
        this.props = props;
    }

    public StoredFile upload(Path file, long companyId, String originalName) {
        return files.store(file, companyId, FileCategory.AUDIO, originalName, CONTENT_TYPE);
    }

    public void deleteLocalCopy(Path file) {
        if (!props.deleteLocalAfterUpload() || file == null) {
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
