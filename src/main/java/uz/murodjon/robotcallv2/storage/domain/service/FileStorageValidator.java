package uz.murodjon.robotcallv2.storage.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.Set;

/** Domain validator for file uploads. */
public final class FileStorageValidator {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp"
    );

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private FileStorageValidator() {
    }

    public static void validateImage(String contentType, long sizeBytes) {
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_TYPE_UNSUPPORTED, contentType, ALLOWED_IMAGE_TYPES);
        }
        if (sizeBytes > MAX_IMAGE_SIZE_BYTES) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_TOO_LARGE);
        }
    }
}
