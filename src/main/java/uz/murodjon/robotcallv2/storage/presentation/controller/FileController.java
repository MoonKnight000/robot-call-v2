package uz.murodjon.robotcallv2.storage.presentation.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.storage.application.dto.FileUploadResponse;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;

/**
 * Serves every file this app ever hands to a browser and handles unified file uploads.
 */
@RequestMapping("/api")
public interface FileController {

    /**
     * The file's bytes. Images and call recordings come back {@code inline} so a browser
     * plays them in place; a {@code Range} request is answered with {@code 206} and just
     * that slice, which is how an audio player seeks.
     */
    @GetMapping("/files/{id}")
    ResponseEntity<Resource> download(@PathVariable long id,
                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range);

    @PostMapping(value = {"/files/upload", "/v1/files/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResponseData<FileUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) FileCategory category,
            @RequestParam(value = "companyId", required = false) Long companyId);
}
