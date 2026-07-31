package uz.murodjon.uysotvoice.report.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.report.dto.RecordingFile;
import uz.murodjon.uysotvoice.report.dto.RecordingLocation;
import uz.murodjon.uysotvoice.report.dto.RecordingRedirect;

import java.net.URI;

/**
 * Turns the location {@code ReportService} resolved into the HTTP response for it.
 *
 * <p>A recording is served two different ways depending on where it ended up, and neither
 * branch belongs in the controller: a controller method is a single delegation, and this
 * is a genuine choice with two shapes. It is not service work either — the service decided
 * <em>where the file is</em>; picking the status code and headers is a web concern, so it
 * lives in the web layer but outside the controller.
 */
@Component
public class RecordingResponseFactory {

    private static final MediaType WAV = MediaType.parseMediaType("audio/wav");

    /**
     * @return a 302 to object storage, or the file itself as a download. Redirecting
     *         rather than proxying keeps megabytes of audio out of this service.
     */
    public ResponseEntity<Resource> toResponse(RecordingLocation location) {
        return switch (location) {
            case RecordingRedirect redirect -> ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirect.url()))
                    .build();
            case RecordingFile file -> ResponseEntity.ok()
                    .contentType(WAV)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.filename() + "\"")
                    .body(new FileSystemResource(file.path()));
        };
    }
}
