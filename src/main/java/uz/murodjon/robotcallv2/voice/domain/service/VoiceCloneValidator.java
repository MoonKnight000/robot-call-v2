package uz.murodjon.robotcallv2.voice.domain.service;

import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

/**
 * What a voice clone needs before anything is sent to the vendor: the speaker's consent and
 * a few audio recordings of them. A voice is a person's likeness — cloning one without their
 * agreement is the misuse this check exists to refuse.
 */
public final class VoiceCloneValidator {

    /** More than this adds little to an instant clone and only slows the upload. */
    public static final int MAX_SAMPLES = 5;

    private VoiceCloneValidator() {
    }

    public static void validate(Boolean consent, List<UploadedFile> samples) {
        if (!Boolean.TRUE.equals(consent)) {
            throw new ValidationException(ErrorCode.VOICE_CLONE_CONSENT_REQUIRED);
        }
        if (samples == null || samples.isEmpty() || samples.stream().allMatch(s -> s == null || s.isEmpty())) {
            throw new ValidationException(ErrorCode.AUDIO_UPLOAD_FILE_MISSING);
        }
        if (samples.size() > MAX_SAMPLES) {
            throw new ValidationException(ErrorCode.VOICE_CLONE_TOO_MANY_SAMPLES, MAX_SAMPLES);
        }
        for (UploadedFile sample : samples) {
            if (sample == null || sample.isEmpty()) {
                throw new ValidationException(ErrorCode.AUDIO_UPLOAD_FILE_MISSING);
            }
            String type = sample.contentType();
            if (type == null || !type.startsWith("audio/")) {
                throw new ValidationException(ErrorCode.VOICE_CLONE_SAMPLE_NOT_AUDIO, sample.originalFilename());
            }
        }
    }
}
