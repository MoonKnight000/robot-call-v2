package uz.murodjon.uysotvoice.storage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.storage.config.AudioStorageProperties;
import uz.murodjon.uysotvoice.storage.dto.StoredFile;
import uz.murodjon.uysotvoice.storage.enums.FileCategory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Uploads finished call recordings to MinIO/S3 via {@link FileStorageService} (Stage 9).
 * Disabled by default — when off (or on any error) the recording stays on local disk
 * and {@link #upload} returns {@code null}, leaving {@code recording_file_id} empty (so
 * it exists on disk but is not retrievable through the API — accepted tradeoff of
 * running with storage off, same as before this class delegated to {@code stored_file}).
 */
@Component
public class AudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(AudioStorageService.class);
    private static final String CONTENT_TYPE = "audio/wav";

    private final FileStorageService files;
    private final AudioStorageProperties props;

    public AudioStorageService(FileStorageService files, AudioStorageProperties props) {
        this.files = files;
        this.props = props;
    }

    /**
     * Upload {@code file} as {@code originalName} under {@code companyId}; returns the
     * catalog row or {@code null} if storage is unavailable or the upload fails.
     */
    public StoredFile upload(Path file, long companyId, String originalName) {
        return files.store(file, companyId, FileCategory.AUDIO, originalName, CONTENT_TYPE);
    }

    /**
     * Delete the local WAV now that object storage holds it. Call only after a
     * successful {@link #upload} — otherwise this throws away the only copy.
     * No-op when {@code delete-local-after-upload} is off.
     */
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
