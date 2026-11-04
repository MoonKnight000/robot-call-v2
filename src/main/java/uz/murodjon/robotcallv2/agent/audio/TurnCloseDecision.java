package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.agent.turn.TurnCue;

/**
 * What ended one utterance at the {@link SpeechGate}: the wait rule that applied, what was added
 * to it, and what each turn signal said at that moment. Recorded next to whether the caller then
 * carried on, so the closes that cut callers off can be traced to the signal that made them
 * (model-driven turn plan, phase 0).
 *
 * @param waitRule      the rule that chose the base wait ({@code post-roll}, {@code complete-words},
 *                      {@code closed-answer}, {@code turn-model-early}, …)
 * @param waitMs        that base wait
 * @param extensionRule what the extension was decided on; {@code none} when nothing was added
 * @param extensionMs   the extension
 * @param turnModel     the turn model's (Smart Turn / text turn) verdict: {@code complete},
 *                      {@code unfinished} or {@code not-asked}
 * @param llm           the LLM's verdict (speculative reply or LLM turn detector)
 * @param words         what the word rules ({@code TranscriptTurnCues}) read in the last interim
 * @param restarts      pauses the turn model had judged that VAD speech then started over
 * @param policy        the {@code TurnPolicy} that combined the verdicts ({@code auto}, {@code mixed}, …)
 */
public record TurnCloseDecision(String waitRule, int waitMs, String extensionRule, int extensionMs,
                                String turnModel, TurnCue llm, TurnCue words, int restarts, String policy) {

    /** The log form: {@code "post-roll 1200ms + words-unfinished 500ms (turn-model=complete, …)"}. */
    public String describe() {
        return waitRule + " " + waitMs + "ms + " + extensionRule + " " + extensionMs + "ms (turn-model="
                + turnModel + ", llm=" + llm + ", words=" + words + ", restarts-after-verdict=" + restarts + ")";
    }
}
