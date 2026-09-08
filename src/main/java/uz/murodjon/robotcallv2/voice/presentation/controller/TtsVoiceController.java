package uz.murodjon.robotcallv2.voice.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

/**
 * The voices an agent can be created with ({@code GET /api/tts/voices}).
 *
 * <p>Narrowed by the agent's pipeline rather than by the company: a cascade voice is
 * spoken by a TTS vendor and a realtime voice by the speech-to-speech engine, and the two
 * sets of names have nothing to do with each other. Omitting {@code mode} returns both.
 */
@RequestMapping("/api/tts")
public interface TtsVoiceController {

    @GetMapping("/voices")
    ResponseEntity<ResponseData<List<TtsVoice>>> voices(@RequestParam(required = false) PipelineMode mode,
                                                         @RequestParam(required = false) String language);
}
