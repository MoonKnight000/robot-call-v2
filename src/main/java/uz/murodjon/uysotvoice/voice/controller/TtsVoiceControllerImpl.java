package uz.murodjon.uysotvoice.voice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.voice.dto.TtsVoice;
import uz.murodjon.uysotvoice.voice.service.TtsVoiceService;

import java.util.List;

@RestController
public class TtsVoiceControllerImpl implements TtsVoiceController {

    private final TtsVoiceService service;

    public TtsVoiceControllerImpl(TtsVoiceService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<List<TtsVoice>>> voices(String language) {
        return ResponseEntity.ok(ResponseData.ok(service.findSelectable(language)));
    }
}
