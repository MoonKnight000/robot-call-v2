package uz.murodjon.robotcallv2.storage.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.security.CompanyAccessGuard;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.dto.FileUploadResponse;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.storage.application.port.output.ObjectStoragePort;
import uz.murodjon.robotcallv2.storage.application.port.output.StoredFileRepository;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.domain.service.FileStorageValidator;
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The single entry point every feature uses to put a file in MinIO and get it back out.
 */
@Service
public class FileStorageService implements FileStorageUseCase {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final ObjectStoragePort objectStoragePort;
    private final StoredFileRepository storedFileRepository;
    private final CompanyAccessGuard companyAccessGuard;
    private final String bucket;

    public FileStorageService(ObjectStoragePort objectStoragePort, StoredFileRepository storedFileRepository,
                              CompanyAccessGuard companyAccessGuard,
                              AudioStorageProperties audioStorageProperties) {
        this.objectStoragePort = objectStoragePort;
        this.storedFileRepository = storedFileRepository;
        this.companyAccessGuard = companyAccessGuard;
        this.bucket = audioStorageProperties.bucket();
    }

    @Override
    public FileUploadResponse upload(long callerCompanyId, UploadedFile file, Long companyId,
                                     FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_FILE_MISSING);
        }
        long resolvedCompanyId = companyId != null ? companyId : callerCompanyId;
        if (companyId != null) {
            companyAccessGuard.requireOwnOrSuperadmin(callerCompanyId, companyId);
        }
        byte[] data = file.bytes();

        // The bytes decide the type; the browser's Content-Type only breaks ties the bytes
        // cannot. What is stored — and served back inline — is therefore always one of the
        // allowed formats, whatever the upload called itself.
        String contentType = FileStorageValidator.detectContentType(data, file.contentType());
        FileCategory resolvedCategory = category != null ? category : detectCategory(contentType);
        String originalName = file.originalFilename() != null && !file.originalFilename().isBlank()
                ? file.originalFilename()
                : "file";

        StoredFile stored = store(data, resolvedCompanyId, resolvedCategory, originalName, contentType);
        if (stored == null) {
            throw new ExternalServiceException(ErrorCode.IMAGE_UPLOAD_STORAGE_UNAVAILABLE, "object-storage");
        }
        return FileUploadResponse.of(stored);
    }

    private static FileCategory detectCategory(String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase();
            if (lower.startsWith("image/")) {
                return FileCategory.IMAGE;
            }
            if (lower.startsWith("audio/")) {
                return FileCategory.AUDIO;
            }
        }
        return FileCategory.DOCUMENT;
    }

    @Override
    public StoredFile store(byte[] data, long companyId, FileCategory category, String originalName,
                            String contentType) {
        String objectKey = objectKey(companyId, category, originalName, contentType);
        if (!objectStoragePort.upload(data, bucket, objectKey, contentType)) {
            return null;
        }
        return save(companyId, category, originalName, objectKey, contentType, data.length);
    }

    @Override
    public StoredFile store(Path file, long companyId, FileCategory category, String originalName,
                            String contentType) {
        String objectKey = objectKey(companyId, category, originalName, contentType);
        if (!objectStoragePort.uploadFile(file, bucket, objectKey, contentType)) {
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

    @Override
    public DownloadableFile download(long callerCompanyId, long id) {
        StoredFile file = storedFileRepository.find(id);
        if (file == null) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND, id);
        }
        companyAccessGuard.requireOwnOrSuperadmin(callerCompanyId, file.companyId());
        try {
            InputStream content = objectStoragePort.download(file.bucket(), file.path());
            return new DownloadableFile(file, content);
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.FILE_READ_FAILED, "object-storage", e, id);
        }
    }

    @Override
    public boolean delete(long companyId, long id) {
        StoredFile file = storedFileRepository.find(id);
        if (file == null || file.companyId() != companyId) {
            return false;
        }
        try {
            objectStoragePort.delete(file.bucket(), file.path());
        } catch (Exception e) {
            log.warn("Stored file {} ({}/{}) could not be deleted: {}", id, file.bucket(), file.path(), e.getMessage());
            return false;
        }
        storedFileRepository.delete(id);
        return true;
    }

    private StoredFile save(long companyId, FileCategory category, String originalName, String objectKey,
                            String contentType, long sizeBytes) {
        long id = storedFileRepository.create(companyId, category, originalName, objectKey, bucket, contentType, sizeBytes);
        log.info("Stored {}/{} ({} bytes) as file {}", bucket, objectKey, sizeBytes, id);
        return storedFileRepository.find(id);
    }

    /**
     * The extension comes from the type, and only from a type this app knows; the client's
     * file name never reaches the object key (it could carry {@code ..} or a NUL). A type
     * without a known extension — an internal store of a recording in some other container —
     * falls back to the name's own suffix, letters and digits only.
     */
    private static String objectKey(long companyId, FileCategory category, String originalName, String contentType) {
        String extension = FileStorageValidator.extensionFor(contentType);
        if (extension.isEmpty()) {
            extension = safeExtensionOf(originalName);
        }
        return "com-" + companyId + "/" + category.folder() + "/" + UUID.randomUUID() + extension;
    }

    private static String safeExtensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String suffix = originalName.substring(dot + 1);
        return suffix.matches("[A-Za-z0-9]{1,8}") ? "." + suffix.toLowerCase() : "";
    }
}
