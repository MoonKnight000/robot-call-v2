package uz.murodjon.robotcallv2.integration.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.Set;

/** Pure rules for an integration provider's icon. */
public final class IntegrationIconValidator {

    public static final int MAX_BYTES = 128 * 1024;
    public static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");

    private IntegrationIconValidator() {
    }

    /** A non-empty image of an allowed type and at most {@link #MAX_BYTES}. */
    public static void validate(String contentType, long size) {
        if (size == 0) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_FILE_MISSING);
        }
        if (size > MAX_BYTES) {
            throw new ValidationException(ErrorCode.INTEGRATION_ICON_TOO_LARGE, MAX_BYTES / 1024);
        }
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException(ErrorCode.INTEGRATION_ICON_TYPE_UNSUPPORTED, contentType,
                    ALLOWED_CONTENT_TYPES);
        }
    }
}
