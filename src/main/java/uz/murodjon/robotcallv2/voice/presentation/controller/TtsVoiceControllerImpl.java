package uz.murodjon.robotcallv2.voice.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

@RestController
public class TtsVoiceControllerImpl implements TtsVoiceController {

    private final TtsVoiceUseCase ttsVoiceUseCase;

    public TtsVoiceControllerImpl(TtsVoiceUseCase ttsVoiceUseCase) {
        this.ttsVoiceUseCase = ttsVoiceUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<TtsVoice>>> voices(String language) {
        return ResponseEntity.ok(ResponseData.ok(ttsVoiceUseCase.findSelectableForCurrentCompany(language)));
    }
}
