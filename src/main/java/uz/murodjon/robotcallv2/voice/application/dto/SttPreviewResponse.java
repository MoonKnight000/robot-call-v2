package uz.murodjon.robotcallv2.voice.application.dto;

import java.util.List;

/**
 * {@code POST /api/stt/preview} response — what one recognizer heard in the uploaded file.
 *
 * @param provider             the STT provider that transcribed it
 * @param language             BCP-47 language the recognizer was told to expect
 * @param alternativeLanguages the other languages it was allowed to hear in the same recording
 * @param transcript           recognized text; empty when the provider returned nothing
 * @param audioMs              length of the uploaded audio
 */
public record SttPreviewResponse(
        String provider,
        String language,
        List<String> alternativeLanguages,
        String transcript,
        long audioMs
) {
}
