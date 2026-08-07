package uz.murodjon.uysotvoice.storage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.company.service.CompanyAccessGuard;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.storage.config.AudioStorageProperties;
import uz.murodjon.uysotvoice.storage.dto.DownloadableFile;
import uz.murodjon.uysotvoice.storage.dto.StoredFile;
import uz.murodjon.uysotvoice.storage.enums.FileCategory;
import uz.murodjon.uysotvoice.storage.repository.StoredFileRepository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The single entry point every feature uses to put a file in MinIO and get it back out
 * — company logos/user avatars ({@code ImageUploadService}), call recordings ({@code
 * AudioStorageService}), and (once a feature needs it) documents. Owns the {@code
 * stored_file} catalog row that turns an opaque MinIO object key into something a
 * frontend can ask for by id via {@code GET /api/files/{id}}, instead of ever being
 * handed a raw MinIO URL — see {@code ObjectStorageService}'s javadoc for why.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final ObjectStorageService storage;
    private final StoredFileRepository files;
    private final CompanyAccessGuard access;
    private final String bucket;

    public FileStorageService(ObjectStorageService storage, StoredFileRepository files,
                               CompanyAccessGuard access, AudioStorageProperties props) {
        this.storage = storage;
        this.files = files;
        this.access = access;
        this.bucket = props.bucket();
    }

    /**
     * Buffers {@code data} into MinIO — used for browser-submitted uploads (images),
     * small enough to hold in memory. {@code null} on failure (storage unavailable or
     * the upload errored) — callers that must not degrade silently (image uploads)
     * throw {@link ExternalServiceException} themselves on a {@code null} result, same
     * as before this file existed.
     */
    public StoredFile store(byte[] data, long companyId, FileCategory category, String originalName,
                             String contentType) {
        String objectKey = objectKey(companyId, category, originalName);
        if (!storage.upload(data, bucket, objectKey, contentType)) {
            return null;
        }
        return save(companyId, category, originalName, objectKey, contentType, data.length);
    }

    /**
     * As {@link #store(byte[], long, FileCategory, String, String)}, streaming from a
     * local file — used for call recordings, which can run larger than comfortable to
     * buffer.
     */
    public StoredFile store(Path file, long companyId, FileCategory category, String originalName,
                             String contentType) {
        String objectKey = objectKey(companyId, category, originalName);
        if (!storage.uploadFile(file, bucket, objectKey, contentType)) {
            return null;
        }
        long sizeBytes;
        try {
            sizeBytes = Files.size(file);
        } catch (Exception e) {
            sizeBytes = 0;
        }
        return save(companyId, category, originalName, objectKey, contentType, sizeBytes);
    }

    /**
     * Resolves {@code id} and opens a stream on its bytes. {@link CompanyAccessGuard}
     * enforces the same "own company, or SUPERADMIN" boundary every other cross-tenant
     * lookup in this codebase does — a missing id and another company's id both surface
     * as {@link NotFoundException}, never revealing which ids exist. {@link
     * ExternalServiceException} if MinIO itself is unreachable.
     */
    public DownloadableFile download(long id) {
        StoredFile file = files.find(id);
        if (file == null) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND, id);
        }
        access.requireOwnOrSuperadmin(file.companyId());
        try {
            InputStream content = storage.download(file.bucket(), file.path());
            return new DownloadableFile(file, content);
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.FILE_READ_FAILED, "object-storage", e, id);
        }
    }

    private StoredFile save(long companyId, FileCategory category, String originalName, String objectKey,
                             String contentType, long sizeBytes) {
        long id = files.create(companyId, category, originalName, objectKey, bucket, contentType, sizeBytes);
        log.info("Stored {}/{} ({} bytes) as file {}", bucket, objectKey, sizeBytes, id);
        return files.find(id);
    }

    private static String objectKey(long companyId, FileCategory category, String originalName) {
        return "com-" + companyId + "/" + category.folder() + "/" + UUID.randomUUID() + extensionOf(originalName);
    }

    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot);
    }
}
