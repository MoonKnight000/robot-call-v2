package uz.murodjon.uysotvoice.storage.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.storage.dto.DownloadableFile;
import uz.murodjon.uysotvoice.storage.enums.FileCategory;

/**
 * Turns a resolved {@link DownloadableFile} into the HTTP response for it — a web
 * concern ({@code Content-Type}/{@code Content-Disposition}), kept out of both the
 * controller (no logic allowed there) and the service (doesn't know about HTTP).
 */
@Component
public class FileResponseFactory {

    public ResponseEntity<Resource> toResponse(DownloadableFile file) {
        MediaType contentType = file.meta().format() != null
                ? MediaType.parseMediaType(file.meta().format())
                : MediaType.APPLICATION_OCTET_STREAM;
        String disposition = file.meta().category() == FileCategory.IMAGE ? "inline" : "attachment";
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + file.meta().originalName() + "\"")
                .body(new InputStreamResource(file.content()));
    }
}
