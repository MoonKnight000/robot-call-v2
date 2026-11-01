package uz.murodjon.robotcallv2.campaign.presentation.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.campaign.application.dto.TargetsExport;

/** Wraps a rendered targets file as a browser download. */
@Component
public class TargetsExportResponseFactory {

    public ResponseEntity<byte[]> toResponse(TargetsExport export) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(export.filename()).build());
        return ResponseEntity.ok().headers(headers).body(export.body());
    }
}
