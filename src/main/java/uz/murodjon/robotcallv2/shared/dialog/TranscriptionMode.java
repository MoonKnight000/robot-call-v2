package uz.murodjon.robotcallv2.shared.dialog;

/**
 * How the speech recogniser trades latency for accuracy on a cascade call: {@code FAST}
 * returns transcripts sooner, {@code ACCURATE} waits for a more reliable result.
 */
public enum TranscriptionMode {
    FAST,
    ACCURATE
}
