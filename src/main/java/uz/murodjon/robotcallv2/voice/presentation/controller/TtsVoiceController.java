package uz.murodjon.robotcallv2.voice.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.voice.application.dto.CloneVoiceRequest;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;

/**
 * The voices an agent can be created with ({@code GET /api/tts/voices}), and the company's own
 * cloned ones.
 *
 * <p>Narrowed by the agent's pipeline: a cascade voice is spoken by a TTS vendor and a realtime
 * voice by the speech-to-speech engine, and the two sets of names have nothing to do with each
 * other. Omitting {@code mode} returns both. Every company sees the shared catalog plus its own
 * clones, never another company's.
 */
@RequestMapping("/api/tts")
public interface TtsVoiceController {

    @GetMapping("/voices")
    ResponseEntity<ResponseData<List<TtsVoice>>> voices(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) PipelineMode mode,
            @RequestParam(required = false) String language);

    /**
     * Clones a voice from 1–5 recordings of one speaker (ElevenLabs instant cloning). The form
     * carries {@code name}, {@code language}, {@code consent=true} and the recordings as
     * {@code files}. The new voice is picked for an agent by its {@code id}, like any other.
     */
    @PreAuthorize("hasAuthority('VOICE_EDIT')")
    @PostMapping(value = "/voices/clone", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResponseData<TtsVoice>> cloneVoice(
            @CurrentCompanyId long companyId,
            @Valid @ModelAttribute CloneVoiceRequest request,
            @RequestParam(value = "files", required = false) List<MultipartFile> files);

    /** Deletes one of the company's cloned voices; shared catalog voices cannot be deleted. */
    @PreAuthorize("hasAuthority('VOICE_EDIT')")
    @DeleteMapping("/voices/{id}")
    ResponseEntity<ResponseData<Void>> deleteClonedVoice(
            @CurrentCompanyId long companyId,
            @PathVariable String id);
}
