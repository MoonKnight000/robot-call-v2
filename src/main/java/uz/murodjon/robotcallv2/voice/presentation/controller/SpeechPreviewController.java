package uz.murodjon.robotcallv2.voice.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voice.application.dto.PreviewVoiceRequest;
import uz.murodjon.robotcallv2.voice.application.dto.SttPreviewResponse;

/**
 * Trying voices and recognizers out from the settings screen, without placing a call.
 */
@RequestMapping("/api")
public interface SpeechPreviewController {

    /**
     * The line spoken by catalog voice {@code id}, as an {@code audio/wav} body the browser
     * can hand straight to an {@code <audio>} element.
     */
    @PreAuthorize("hasAuthority('VOICE_READ')")
    @PostMapping("/tts/voices/{id}/preview")
    ResponseEntity<Resource> previewVoice(@PathVariable String id, @Valid @RequestBody PreviewVoiceRequest request);

    /**
     * The uploaded recording (WebM/Opus, MP4, WAV — anything ffmpeg reads) transcribed by
     * one STT provider. Runs at the recording's own pace,
     * so a 10-second file takes about 10 seconds to answer.
     */
    @PreAuthorize("hasAuthority('VOICE_READ')")
    @PostMapping(value = "/stt/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResponseData<SttPreviewResponse>> previewStt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String language);
}
