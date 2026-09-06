package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.report.application.dto.ReportDownload;

/**
 * Turns a rendered {@link ReportDownload} into the HTTP response. The renderers below
 * {@code application} produce bytes and a content type; the {@code Content-Disposition}
 * that makes a browser save them under a name is only meaningful here.
 */
@Component
public class ReportDownloadResponseFactory {

    public ResponseEntity<byte[]> toResponse(ReportDownload download) {
        return ResponseEntity.ok()
                .headers(headers(download))
                .body(download.body());
    }

    public ResponseEntity<Resource> toResourceResponse(ReportDownload download) {
        return ResponseEntity.ok()
                .headers(headers(download))
                .body(new ByteArrayResource(download.body()));
    }

    private static HttpHeaders headers(ReportDownload download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + download.filename() + "\"");
        return headers;
    }
}
