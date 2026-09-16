package uz.murodjon.robotcallv2.agent.stt;

/**
 * Streams only what the VAD scores as speech, with margins on both sides
 * (see {@code SpeechGate}). Requires VAD to be enabled and its model present;
 * without it the gate is never installed and every frame is streamed.
 *
 * <p>On, and the reason is the turnaround: leaving the end-of-utterance decision to
 * SpeechKit put 1.2–2.3 s in front of every reply — the single largest block of the wait,
 * and why a turn was heard as 4–6 s (VOICE-QUALITY-PLAN A.1). It was off before that, and
 * the reason is worth keeping in view: a gate that closes early cuts the beginning of the
 * next utterance off, and one that opens late drops the first word. {@code preRollMs} is
 * what pays for the second half of that.
 *
 * <p>It needs a working VAD. With no {@code voice-agent.vad.model-path} the gate is never
 * built ({@code AudioPipelineAssembler#buildAudioListeners}) and everything here is silently inert —
 * the same condition that disables barge-in, and the app says so at startup.
 *
 * @param enabled     master switch. Turn off if transcripts start losing words or
 *                    utterances stop being finalized — the saving is never worth a
 *                    missed turn. Off puts the whole decision back on the recognizer,
 *                    where {@code stt.yandex.eou-max-pause-hint-ms} becomes the budget
 *                    again; no deploy needed
 * @param preRollMs   how much audio before the first speech window is kept and sent
 *                    once the gate opens. Must cover the VAD's own detection delay,
 *                    or every utterance arrives with its first syllable missing
 * @param postRollMs  how long the stream stays open after speech stops — the top of the
 *                    endpointing budget, the silence a long answer waits out before the
 *                    turn starts. Too short and the final transcript never arrives, which
 *                    stalls the whole turn.
 *                    <p>1200, up from 700, and that direction costs latency on purpose.
 *                    {@code TurnDatasetTool} labelled every caller pause in the recorded
 *                    calls (VOICE-QUALITY-PLAN C.4) and the two classes came out as:
 *                    pauses the caller resumed from run 204–1824 ms, pauses they were
 *                    actually finished at start at 1084 ms. A third of the mid-sentence
 *                    pauses were longer than 700 ms — those callers were being cut off and
 *                    answered mid-thought, which costs a whole turn to recover and
 *                    sometimes the call. 1200 covers all but the two longest.
 *                    <p>What is not paid for it: "ha", "yo'q", "to'ladim" close at
 *                    {@code endpointing.short-silence-ms} and never reach this, and
 *                    speculation ({@code dialog.preemptive} + {@code preemptive-tts})
 *                    writes the reply and synthesizes its first sentence during this
 *                    silence — on a hit the extra 500 ms is dead time for the recognizer,
 *                    not for the caller. The real fix is a turn detector that reads the
 *                    1.1–1.8 s overlap semantically instead of guessing at a threshold;
 *                    until uz-UZ has one, this errs toward waiting
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
