package uz.murodjon.robotcallv2.storage.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Domain validator for file uploads. The type of an uploaded file is decided from its
 * bytes, never from the {@code Content-Type} the browser declared: a declared type is a
 * request, and honouring it is how an SVG with a script inside gets stored — and served
 * back {@code inline} — as an image.
 */
public final class FileStorageValidator {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp"
    );

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Types accepted through {@code POST /api/files/upload}, and the extension each is stored under. */
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/webp", ".webp"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/ogg", ".ogg"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry(DOCX, ".docx"),
            Map.entry(XLSX, ".xlsx"),
            Map.entry("text/csv", ".csv"),
            Map.entry("text/plain", ".txt")
    );

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final byte[] WAVE = {'W', 'A', 'V', 'E'};
    private static final byte[] ID3 = {'I', 'D', '3'};
    private static final byte[] OGG = {'O', 'g', 'g', 'S'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] ZIP = {'P', 'K', 3, 4};

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

    /**
     * The canonical type of {@code data}, read from its signature, or a
     * {@link ValidationException} when the bytes are not one of the allowed formats.
     * {@code declaredType} only disambiguates what the bytes cannot: a zip container is
     * DOCX or XLSX by declaration, and text is CSV or plain by declaration.
     */
    public static String detectContentType(byte[] data, String declaredType) {
        String declared = declaredType == null ? "" : declaredType.toLowerCase().split(";")[0].trim();
        if (startsWith(data, PNG)) {
            return "image/png";
        }
        if (startsWith(data, JPEG)) {
            return "image/jpeg";
        }
        if (startsWith(data, RIFF) && matchesAt(data, 8, WEBP)) {
            return "image/webp";
        }
        if (startsWith(data, RIFF) && matchesAt(data, 8, WAVE)) {
            return "audio/wav";
        }
        if (startsWith(data, ID3) || isMpegFrame(data)) {
            return "audio/mpeg";
        }
        if (startsWith(data, OGG)) {
            return "audio/ogg";
        }
        if (startsWith(data, PDF)) {
            return "application/pdf";
        }
        if (startsWith(data, ZIP) && (DOCX.equals(declared) || XLSX.equals(declared))) {
            return declared;
        }
        if (("text/csv".equals(declared) || "text/plain".equals(declared)) && isPlainText(data)) {
            return declared;
        }
        if (declared.isEmpty()) {
            throw new ValidationException(ErrorCode.FILE_TYPE_UNRECOGNIZED);
        }
        throw new ValidationException(ErrorCode.FILE_TYPE_NOT_ALLOWED, declared);
    }

    /** The extension a file of {@code contentType} is stored under — derived, never taken from the client's name. */
    public static String extensionFor(String contentType) {
        return EXTENSIONS.getOrDefault(contentType, "");
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        return matchesAt(data, 0, signature);
    }

    private static boolean matchesAt(byte[] data, int offset, byte[] signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        return Arrays.equals(data, offset, offset + signature.length, signature, 0, signature.length);
    }

    /** An MPEG audio frame header: 11 sync bits set. */
    private static boolean isMpegFrame(byte[] data) {
        return data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0;
    }

    /** UTF-8 text with no NUL bytes and no markup at the start — a CSV, not an HTML page renamed. */
    private static boolean isPlainText(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return false;
            }
        }
        String head = new String(data, 0, Math.min(data.length, 256), StandardCharsets.UTF_8).stripLeading();
        return !head.startsWith("<");
    }
}
