package uz.murodjon.robotcallv2.shared.api;

import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.io.IOException;

/**
 * An uploaded file as a service sees it: read once in the controller, so no service
 * depends on the servlet multipart API. The bytes are bounded by
 * {@code spring.servlet.multipart.max-file-size} before this record is ever built.
 */
public record UploadedFile(String originalFilename, String contentType, long size, byte[] bytes) {

    /** Null for a missing part, so the service still answers with its own "file missing" code. */
    public static UploadedFile from(MultipartFile file) {
        if (file == null) {
            return null;
        }
        try {
            return new UploadedFile(file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getBytes());
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.IMAGE_UPLOAD_READ_FAILED, e.getMessage());
        }
    }

    public boolean isEmpty() {
        return size == 0;
    }
}
