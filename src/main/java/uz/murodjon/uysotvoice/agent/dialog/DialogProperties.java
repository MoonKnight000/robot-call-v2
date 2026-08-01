package uz.murodjon.uysotvoice.agent.dialog;

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
 * @param mandatoryDisclosure speak the §11.1 notice ("this is an automated system, the
 *                       call is recorded") from code before the model's first turn.
 *                       The requirement is legal; leaving it to the prompt means a
 *                       model that skips it puts the call on the wrong side of §11
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
 * @param factGuard      verify money figures in the model's reply against the injected
 *                       facts before speaking them (§4.4). A hallucinated debt amount
 *                       is the worst output this system has; the prompt forbids it, and
 *                       this is the check that enforces it
 * @param maxTokensPerCall cumulative LLM tokens one call may spend before it is closed
 *                       politely. Bounds the cost of a call that loops or refuses to
 *                       end. 0 disables the budget
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
        boolean mandatoryDisclosure,
        boolean stateScopedTools,
        int historyMaxMessages,
        int noInputSeconds,
        int noInputMaxPrompts,
        boolean factGuard,
        long maxTokensPerCall,
        TestContextProperties testContext
) {
}
