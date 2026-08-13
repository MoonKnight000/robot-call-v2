package uz.murodjon.uysotvoice.agent.stt;

/**
 * Streams only what the VAD scores as speech, with margins on both sides
 * (see {@code SpeechGate}). Requires VAD to be enabled and its model present;
 * without it the gate is never installed and every frame is streamed.
 *
 * @param enabled     master switch. Turn off if transcripts start losing words or
 *                    utterances stop being finalized — the saving is never worth a
 *                    missed turn
 * @param preRollMs   how much audio before the first speech window is kept and sent
 *                    once the gate opens. Must cover the VAD's own detection delay,
 *                    or every utterance arrives with its first syllable missing
 * @param postRollMs  how long the stream stays open after speech stops. This is the
 *                    silence the provider's end-of-utterance detector listens for;
 *                    too short and the final transcript never arrives, which stalls
 *                    the whole turn
 * @param minSpeechMs continuous speech required before the gate opens. Keeps a blip —
 *                    a cough, a door, a voice across the room — from being streamed and
 *                    coming back as a one-word transcript the dialog answers. Must stay
 *                    well under {@code preRollMs}, which is what replays the run-up
 */
public record VadGatingProperties(
        boolean enabled,
        int preRollMs,
        int postRollMs,
        int minSpeechMs
) {
}
