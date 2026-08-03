package uz.murodjon.uysotvoice.voice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.voice.dto.TtsVoice;

import java.util.List;

/**
 * The voices a campaign can be created with ({@code GET /api/tts/voices}). The campaign
 * form reads it to fill its voice picker, so the options an operator sees are exactly
 * the ids {@code POST /api/campaigns} accepts.
 */
@RequestMapping("/api/tts")
public interface TtsVoiceController {

    /**
     * @param language optional BCP-47 filter, so a form that already knows the campaign
     *                 language only offers voices that can speak it
     */
    @GetMapping("/voices")
    ResponseEntity<ResponseData<List<TtsVoice>>> voices(@RequestParam(required = false) String language);
}
