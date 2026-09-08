package uz.murodjon.robotcallv2.storage.presentation.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Turns a resolved {@link DownloadableFile} into the HTTP response.
 *
 * <p>Media the browser is meant to play in place — an avatar, a call recording — needs
 * three things an {@code attachment} download does not, and an {@code <audio>} element
 * fails differently without each: {@code Content-Disposition: attachment} makes the
 * recording a file to save rather than a track to play, a missing {@code Content-Length}
 * leaves the player with no duration to draw, and without byte ranges the seek bar
 * cannot move. Everything else is still served as a download.
 */
@Component
public class FileResponseFactory {

    /** Categories a browser renders in place rather than saves. */
    private static final List<FileCategory> INLINE = List.of(FileCategory.IMAGE, FileCategory.AUDIO);

    /**
     * @param rangeHeader the request's {@code Range}, or {@code null} when it asked for
     *                    the whole file — a player sends one every time it seeks
     */
    public ResponseEntity<Resource> toResponse(DownloadableFile file, String rangeHeader) {
        MediaType contentType = file.meta().format() != null
                ? MediaType.parseMediaType(file.meta().format())
                : MediaType.APPLICATION_OCTET_STREAM;
        long length = file.meta().sizeBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentDisposition(ContentDisposition
                .builder(INLINE.contains(file.meta().category()) ? "inline" : "attachment")
                .filename(file.meta().originalName())
                .build());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        HttpRange range = firstRange(rangeHeader);
        if (range == null) {
            headers.setContentLength(length);
            return new ResponseEntity<>(new InputStreamResource(file.content()), headers, HttpStatus.OK);
        }

        long start = range.getRangeStart(length);
        long end = range.getRangeEnd(length);
        long count = end - start + 1;
        headers.setContentLength(count);
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length);
        return new ResponseEntity<>(
                new InputStreamResource(slice(file.content(), start, count, file.meta().id())),
                headers, HttpStatus.PARTIAL_CONTENT);
    }

    /**
     * The one range to serve, or {@code null} for the whole file. Only the first of a
     * multi-range request is honoured — answering one range of several is allowed, and
     * no player this serves asks for more than one.
     */
    private static HttpRange firstRange(String rangeHeader) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            return ranges.isEmpty() ? null : ranges.get(0);
        } catch (IllegalArgumentException e) {
            // A range this server cannot make sense of is ignored, which RFC 9110 §14.2
            // allows: the client gets the whole file rather than an error.
            return null;
        }
    }

    /** {@code content} advanced to {@code start} and stopping after {@code count} bytes. */
    private static InputStream slice(InputStream content, long start, long count, long fileId) {
        try {
            content.skipNBytes(start);
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.FILE_READ_FAILED, "object-storage", e, fileId);
        }
        return new FilterInputStream(content) {
            private long left = count;

            @Override
            public int read() throws IOException {
                if (left == 0) {
                    return -1;
                }
                int b = super.read();
                if (b >= 0) {
                    left--;
                }
                return b;
            }

            @Override
            public int read(byte[] buffer, int off, int len) throws IOException {
                if (left == 0) {
                    return -1;
                }
                int read = super.read(buffer, off, (int) Math.min(len, left));
                if (read > 0) {
                    left -= read;
                }
                return read;
            }
        };
    }
}
