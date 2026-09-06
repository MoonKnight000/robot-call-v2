package uz.murodjon.robotcallv2.voice.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

/**
 * The voices a campaign can be created with ({@code GET /api/tts/voices}).
 */
@RequestMapping("/api/tts")
public interface TtsVoiceController {

    @GetMapping("/voices")
    ResponseEntity<ResponseData<List<TtsVoice>>> voices(@CurrentCompanyId long companyId,
                                                         @RequestParam(required = false) String language);
}
