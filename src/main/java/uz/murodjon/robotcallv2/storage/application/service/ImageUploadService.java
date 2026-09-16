package uz.murodjon.robotcallv2.storage.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.storage.application.port.input.ImageUploadUseCase;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

import java.util.Set;

/**
 * Company logo / user avatar uploads (report #4, #11).
 *
 * <p>{@link #MAX_BYTES} and {@code spring.servlet.multipart.max-file-size} move together:
 * Spring Boot's own default is 1 MB, which would reject a file before this check ever
 * runs, so the caller would get a container error instead of {@code ErrorCode}.
 */
@Service
public class ImageUploadService implements ImageUploadUseCase {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final FileStorageUseCase fileStorageUseCase;

    public ImageUploadService(FileStorageUseCase fileStorageUseCase) {
        this.fileStorageUseCase = fileStorageUseCase;
    }

    /** @param companyId the owning company — namespaces the MinIO object key */
    @Override
    public StoredFile upload(UploadedFile file, long companyId) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_FILE_MISSING);
        }
        if (file.size() > MAX_BYTES) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_TOO_LARGE);
        }
        if (file.contentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.contentType())) {
            throw new ValidationException(
                    ErrorCode.IMAGE_UPLOAD_TYPE_UNSUPPORTED, file.contentType(), ALLOWED_CONTENT_TYPES);
        }
        String originalName = file.originalFilename() != null && !file.originalFilename().isBlank()
                ? file.originalFilename()
                : "image";
        StoredFile stored = fileStorageUseCase.store(file.bytes(), companyId, FileCategory.IMAGE, originalName,
                file.contentType());
        if (stored == null) {
            throw new ExternalServiceException(ErrorCode.IMAGE_UPLOAD_STORAGE_UNAVAILABLE, "object-storage");
        }
        return stored;
    }
}
