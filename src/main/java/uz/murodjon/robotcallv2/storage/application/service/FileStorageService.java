package uz.murodjon.robotcallv2.storage.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.robotcallv2.company.application.service.CompanyAccessGuard;
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
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.io.IOException;
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
    public FileUploadResponse upload(long callerCompanyId, MultipartFile file, Long companyId,
                                     FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_FILE_MISSING);
        }
        long resolvedCompanyId = companyId != null ? companyId : callerCompanyId;
        if (companyId != null) {
            companyAccessGuard.requireOwnOrSuperadmin(callerCompanyId, companyId);
        }
        FileCategory resolvedCategory = category != null ? category : detectCategory(file.getContentType());

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_READ_FAILED, e.getMessage());
        }

        String originalName = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                ? file.getOriginalFilename()
                : "file";
        String contentType = file.getContentType() != null && !file.getContentType().isBlank()
                ? file.getContentType()
                : "application/octet-stream";

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

    public StoredFile store(byte[] data, long companyId, FileCategory category, String originalName,
                            String contentType) {
        String objectKey = objectKey(companyId, category, originalName);
        if (!objectStoragePort.upload(data, bucket, objectKey, contentType)) {
            return null;
        }
        return save(companyId, category, originalName, objectKey, contentType, data.length);
    }

    public StoredFile store(Path file, long companyId, FileCategory category, String originalName,
                            String contentType) {
        String objectKey = objectKey(companyId, category, originalName);
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

    private StoredFile save(long companyId, FileCategory category, String originalName, String objectKey,
                            String contentType, long sizeBytes) {
        long id = storedFileRepository.create(companyId, category, originalName, objectKey, bucket, contentType, sizeBytes);
        log.info("Stored {}/{} ({} bytes) as file {}", bucket, objectKey, sizeBytes, id);
        return storedFileRepository.find(id);
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
