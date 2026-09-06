package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * Dialog engine settings. Bound from {@code voice-agent.dialog.*} (PROJECT.md §4, §12).
 *
 * @param enabled        master switch for the LLM dialog engine
 * @param autoStart      when true, the bot greets and drives the conversation as soon
 *                       as media is up (Stage 7 verification without a DB/campaign)
 * @param language       BCP-47 conversation language (also used to route STT/TTS)
 * @param ttsVoice       voice id from the {@code tts_voice} table for manual and
 *                       auto-started calls — campaign calls carry their own choice
 *                       (§2.5). Blank uses the configured provider/voice routing
 * @param greetingDelayMs quiet time between the channel being answered and the first
 *                       word. A phone reports "answered" when it sends the 200 OK, but
 *                       the handset (and a GSM leg especially) needs a few hundred more
 *                       milliseconds before it renders audio, and the person is still
 *                       moving it to their ear. Speak into that gap and the caller
 *                       joins mid-greeting — "assalom" is already gone and only
 *                       "…alaykum" is heard. 0 speaks immediately
 * @param maxTurns       hard cap on conversation turns before auto-closing (§4.4)
 * @param maxCallSeconds hard cap on call duration in seconds before auto-closing (§4.4)
 * @param streaming      consume the LLM reply as a token stream and synthesize it one
 *                       sentence at a time (§7.2). This is what keeps the turnaround
 *                       inside the &lt;1s budget (§1.3) — waiting for the whole reply
 *                       and then the whole synthesis serializes two full latencies.
 *                       Set false to fall back to a single blocking call.
 * @param fillerDelayMs  how long a turn may leave the caller in silence before a short
 *                       "bir soniya" is played over the gap while the LLM is still
 *                       generating (§1.3). It does not make the reply arrive sooner — it
 *                       stops the wait sounding like a dropped line, which is what a
 *                       caller actually reacts to. Only ever fires when the turn is
 *                       genuinely slow, never on the greeting, and never on consecutive
 *                       turns. 0 disables it
 * @param backchannelAfterMs how long a caller may talk before the bot says "aha" under
 *                       them to show it is still listening. A person does this every few
 *                       seconds; a line that stays silent through a long explanation reads
 *                       as nobody being there, and callers stop and say "alo?". It is not
 *                       a turn and never enters the history — see
 *                       {@code SpeechOutput#speakBackchannel}. 0 disables it
 * @param backchannelVolumePercent how loud that is, as a share of the normal speaking
 *                       level. At full volume it is not a backchannel, it is the bot
 *                       talking over the caller
 * @param interjectAfterMs how long a caller may talk before the bot steps in with an
 *                       apology and a check. A caller who has been explaining for half a
 *                       minute has usually stopped answering the question, and a recognizer
 *                       cannot close an utterance that never pauses — the turn simply waits.
 *                       Spoken at full volume, unlike a backchannel: it is meant to take the
 *                       floor. 0 disables it
 * @param falseInterruptionTimeoutMs how long a barge-in waits for the caller's words
 *                       before it is judged to have been noise. A VAD fires on a cough,
 *                       a door, or the bot's own audio coming back off a speakerphone
 *                       just as readily as on speech, and the bot has already gone silent
 *                       by then. If no transcript follows within this window and nothing
 *                       else has claimed the call, the reply resumes from where it was cut
 *                       — the caller hears the rest of the sentence rather than a line
 *                       that stops mid-thought. A real interruption never reaches it: the
 *                       caller's transcript starts a new turn first. 0 disables the resume
 * @param preemptive     start writing the reply from the recognizer's interim transcript,
 *                       while the endpointing silence is still being waited out (§1.3,
 *                       {@link Speculation}). The wait between "the caller stopped making
 *                       noise" and "the recognizer says what they said" is several hundred
 *                       milliseconds the turn currently spends doing nothing; this spends
 *                       it on the LLM instead. Nothing is spoken from a guess — a final
 *                       that says something else cancels it and the turn runs as it always
 *                       did — but a cancelled guess is still billed, so it is only worth
 *                       having while most of them land. Watch
 *                       voice.llm.speculation.hit against .started. Streaming only
 * @param preemptiveTts  carry the guess one stage further: synthesize its first sentence
 *                       into the TTS cache while the caller is still finishing, so a turn
 *                       that adopts it speaks from a cache hit instead of paying a
 *                       synthesis round trip after the caller has stopped. Nothing is ever
 *                       spoken from here — the audio only sits in the cache, and the turn's
 *                       own fact guard still sees the sentence. Costs characters on a
 *                       miss, so it is only worth having on top of a healthy speculation
 *                       hit rate. Requires {@code preemptive}
 * @param preemptiveMinChars how much interim text is worth guessing from. The first
 *                       syllables of an utterance are revised constantly and are almost
 *                       never what the final says, so guessing there buys a cancelled
 *                       request every time
 * @param minInterruptionWords how many words a caller has to say over the bot before it
 *                       counts as having been interrupted, rather than agreed with. A
 *                       person says "aha" while the other one is talking and expects them
 *                       to carry on; the recognizer turns it into a final, and a final
 *                       otherwise starts a turn — so the caller gets a fresh question
 *                       instead of the rest of the sentence they were agreeing with. Only
 *                       ever applies to a reply that was cut off mid-way and can therefore
 *                       be resumed, and only to the words in {@code Backchannels}: a
 *                       one-word answer ("yo'q", "to'ladim") is still an answer. 0 or 1
 *                       disables the filter
 * @param mandatoryDisclosure speak the §11.1 notice ("this is an automated system, the
 *                       call is recorded") from code before the model's first turn.
 *                       The requirement is legal; leaving it to the prompt means a
 *                       model that skips it puts the call on the wrong side of §11
 * @param fastModel      model id for turns where the caller said almost nothing ("ha",
 *                       "to'ladim"). Those are most of a call and are answered the same
 *                       way by any model, while the time-to-first-token is paid on every
 *                       one of them. Blank keeps every turn on the company's configured
 *                       model
 * @param fastModelMaxWords how short "almost nothing" is. Above this the caller is saying
 *                       something, and something is what the larger model is for
 * @param stateScopedTools send only the tools the current FSM state can legitimately
 *                       use, instead of all of them on every turn. Tool declarations
 *                       are re-billed with each request, and a tool that cannot fire
 *                       yet is mostly an opportunity to misfire. Set false to send the
 *                       full set if the model starts missing a transition
 * @param historyMaxMessages hard cap on how many messages of the conversation are
 *                       resent each turn; older ones are dropped in blocks. A normal
 *                       call never reaches it — it bounds the cost of a pathological
 *                       one. 0 disables trimming
 * @param noInputSeconds quiet time on the line before the bot asks whether the caller
 *                       is still there. The engine only acts on a final transcript, so
 *                       without this a caller who says nothing (or a recognizer that
 *                       emits no final) leaves the call mute until
 *                       {@code maxCallSeconds}. 0 disables the watchdog
 * @param noInputMaxPrompts how many times to ask before ending the call as unanswered
 * @param fastPath       settle the turns whose meaning is not in doubt ("adashibsiz",
 *                       "boshqa telefon qilmang", "operatorga ulang") from code, without a
 *                       model round trip ({@link FastPathRouter}). The model reaches the
 *                       same tool anyway, a turnaround and a prompt later. Matching is on
 *                       substrings, so a sentence that merely contains one of those phrases
 *                       ends a call that was going fine — this is the switch that stops it
 * @param knowledgeBase  answer questions this system already knows the answer to ("how do
 *                       I pay?", "which branch?") from a fixed line instead of the model.
 *                       The answer is policy and never varies, so a paraphrase costs a full
 *                       turnaround and is the part most likely to be wrong. Off by default:
 *                       it takes the turn away from the scenario, so the wording has to be
 *                       checked against the company that is actually running
 * @param lowConfidenceThreshold recognition confidence below which the turn is told to
 *                       repeat what it heard back before acting on it. A misheard date
 *                       that reaches {@code recordPaymentPromise} is a promise filed for a
 *                       day the caller never named, and nothing downstream can tell the
 *                       difference. Providers that report no confidence at all (0) are
 *                       never treated as unsure; 0 disables the check
 * @param factGuard      verify money figures in the model's reply against the injected
 *                       facts before speaking them (§4.4). A hallucinated debt amount
 *                       is the worst output this system has; the prompt forbids it, and
 *                       this is the check that enforces it
 * @param factViolationEscalateAfter how many fact-guard violations a REALTIME call may
 *                       collect before it is handed to a human operator. Only realtime
 *                       calls reach this: the cascade pipeline checks the sentence
 *                       before it is spoken and simply withholds it, so there is nothing
 *                       to escalate. A speech-to-speech engine has already said the
 *                       figure by the time anything can read it, and the caller who just
 *                       heard a demand for money they do not owe needs a person, not a
 *                       retry. 1 escalates on the first; 0 disables escalation and
 *                       leaves the violation logged and flagged on the attempt
 * @param maxTokensPerCall cumulative LLM tokens one call may spend before it is closed
 *                       politely. Bounds the cost of a call that loops or refuses to
 *                       end. 0 disables the budget
 * @param preToolSpeech  whether to speak a short intermediate phrase before executing tools
 *                       to prevent dead silence during long or external tool operations
 * @param testContext    static client facts injected into the prompt for verification
 */
@ConfigurationProperties(prefix = "voice-agent.dialog")
public record DialogProperties(
        boolean enabled,
        boolean autoStart,
        String language,
        String ttsVoice,
        int greetingDelayMs,
        int maxTurns,
        int maxCallSeconds,
        boolean streaming,
        int fillerDelayMs,
        int backchannelAfterMs,
        int backchannelVolumePercent,
        int interjectAfterMs,
        int falseInterruptionTimeoutMs,
        boolean preemptive,
        boolean preemptiveTts,
        int preemptiveMinChars,
        int minInterruptionWords,
        boolean mandatoryDisclosure,
        String fastModel,
        int fastModelMaxWords,
        boolean stateScopedTools,
        int historyMaxMessages,
        int noInputSeconds,
        int noInputMaxPrompts,
        boolean fastPath,
        boolean knowledgeBase,
        float lowConfidenceThreshold,
        boolean factGuard,
        int factViolationEscalateAfter,
        long maxTokensPerCall,
        boolean preToolSpeech,
        TestContextProperties testContext
) {
}
