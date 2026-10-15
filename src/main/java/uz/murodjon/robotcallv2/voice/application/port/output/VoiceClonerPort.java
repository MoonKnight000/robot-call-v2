package uz.murodjon.robotcallv2.voice.application.port.output;

import uz.murodjon.robotcallv2.shared.api.UploadedFile;

import java.util.List;

/** A TTS vendor that can make a voice from recordings of a person. */
public interface VoiceClonerPort {

    /** The TTS provider that speaks the clones — {@code tts_voice.provider} of each. */
    String findProviderName();

    /** Whether the vendor is configured (has its key), so a clone can be made at all. */
    boolean isAvailable();

    /** Creates the voice at the vendor and returns the vendor's voice id, sent with every synthesis. */
    String createClone(String name, List<UploadedFile> samples);

    /** Removes the voice at the vendor; a voice the vendor no longer has is not an error. */
    void deleteClone(String providerVoiceId);
}
