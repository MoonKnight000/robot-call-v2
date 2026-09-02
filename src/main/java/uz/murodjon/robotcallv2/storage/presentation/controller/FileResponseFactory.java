package uz.murodjon.robotcallv2.storage.presentation.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

/**
 * Turns a resolved {@link DownloadableFile} into the HTTP response.
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
