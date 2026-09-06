package uz.murodjon.robotcallv2.voice.presentation.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voice.application.dto.PreviewVoiceRequest;
import uz.murodjon.robotcallv2.voice.application.dto.SttPreviewResponse;
import uz.murodjon.robotcallv2.voice.application.port.input.SpeechPreviewUseCase;

@RestController
public class SpeechPreviewControllerImpl implements SpeechPreviewController {

    private static final MediaType AUDIO_WAV = MediaType.parseMediaType("audio/wav");

    private final SpeechPreviewUseCase speechPreviewUseCase;

    public SpeechPreviewControllerImpl(SpeechPreviewUseCase speechPreviewUseCase) {
        this.speechPreviewUseCase = speechPreviewUseCase;
    }

    @Override
    public ResponseEntity<Resource> previewVoice(long companyId, String id, PreviewVoiceRequest request) {
        return ResponseEntity.ok().contentType(AUDIO_WAV)
                .body(new ByteArrayResource(speechPreviewUseCase.previewVoice(companyId, id, request.text())));
    }

    @Override
    public ResponseEntity<ResponseData<SttPreviewResponse>> previewStt(long companyId, MultipartFile file,
                                                                        String provider, String language) {
        return ResponseEntity.ok(ResponseData.ok(
                speechPreviewUseCase.previewStt(companyId, file, provider, language)));
    }
}
