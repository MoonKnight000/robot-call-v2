package uz.murodjon.robotcallv2.storage.application.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

import java.io.IOException;
import java.util.Set;

/**
 * Company logo / user avatar uploads.
 */
@Service
public class ImageUploadService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final FileStorageService files;

    public ImageUploadService(FileStorageService files) {
        this.files = files;
    }

    /** @param companyId the owning company — namespaces the MinIO object key */
    public StoredFile upload(MultipartFile file, long companyId) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_FILE_MISSING);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_TOO_LARGE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ValidationException(
                    ErrorCode.IMAGE_UPLOAD_TYPE_UNSUPPORTED, file.getContentType(), ALLOWED_CONTENT_TYPES);
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_READ_FAILED, e.getMessage());
        }
        String originalName = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                ? file.getOriginalFilename()
                : "image";
        StoredFile stored = files.store(data, companyId, FileCategory.IMAGE, originalName, file.getContentType());
        if (stored == null) {
            throw new ExternalServiceException(ErrorCode.IMAGE_UPLOAD_STORAGE_UNAVAILABLE, "object-storage");
        }
        return stored;
    }
}
