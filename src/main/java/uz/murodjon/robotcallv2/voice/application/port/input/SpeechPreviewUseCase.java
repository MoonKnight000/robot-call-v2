package uz.murodjon.robotcallv2.voice.application.port.input;

import uz.murodjon.robotcallv2.shared.api.UploadedFile;

import uz.murodjon.robotcallv2.voice.application.dto.SttPreviewResponse;

import java.util.List;

/**
 * Trying the speech vendors out before a campaign is created: hear a catalog voice speak a
 * line, or hear how a recognizer transcribes a recording.
 */
public interface SpeechPreviewUseCase {

    /**
     * {@code text} spoken by the catalog voice {@code voiceId}, with the company's own
     * speed/pitch settings applied — so the preview sounds like the calls will.
     *
     * @return a complete 8 kHz mono 16-bit PCM WAV file
     */
    byte[] previewVoice(long companyId, String voiceId, String text);

    /**
     * {@code file} — a browser recording (WebM/Opus, MP4/AAC) or any other audio ffmpeg
     * can read — transcribed by one STT provider.
     *
     * @param provider provider id; {@code null}/blank uses the company's engine default
     * @param language BCP-47 language; {@code null}/blank uses the configured STT default
     * @param alternativeLanguages other languages the recording may switch to; {@code null}/empty uses the configured {@code detect-languages}
     */
    SttPreviewResponse previewStt(long companyId, UploadedFile file, String provider, String language,
                                   List<String> alternativeLanguages);
}
