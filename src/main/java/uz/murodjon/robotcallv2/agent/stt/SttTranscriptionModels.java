package uz.murodjon.robotcallv2.agent.stt;

import uz.murodjon.robotcallv2.shared.dialog.TranscriptionMode;

/**
 * The recognition model one STT provider uses for each {@link TranscriptionMode}
 * ({@code voice-agent.stt.transcription-modes.<provider>.*}). A blank entry means the
 * provider has no model for that mode, and the call keeps the model it would otherwise use.
 *
 * @param fast     model returning transcripts sooner
 * @param accurate model waiting for a more reliable result
 */
public record SttTranscriptionModels(String fast, String accurate) {

    /** @return the model for {@code mode}, or null when none is configured */
    public String findModel(TranscriptionMode mode) {
        if (mode == null) {
            return null;
        }
        String model = mode == TranscriptionMode.FAST ? fast : accurate;
        return model == null || model.isBlank() ? null : model.trim();
    }
}
