package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * What a tool does to the call it was called on: move the FSM, record an outcome, settle
 * a disposition, end the conversation.
 *
 * <p>Exists so {@link DialogTools} and {@link ScenarioToolCallbackFactory} serve both
 * pipelines. The tools carry real guardrails — a promised date in the past is rejected in
 * Java, not in a prompt (§4.4) — and a rule enforced in two places is a rule that will
 * eventually be enforced in one. The two sessions differ in how the conversation is
 * <em>driven</em>, not in what its tools mean.
 *
 * <p>Implemented by {@link DialogSession} (cascade), {@link RealtimeDialogSession}
 * (speech-to-speech) and the panel's tool dry run. There are deliberately no default
 * methods: a pipeline that cannot do something says so in its own implementation, so a
 * new tool or a new pipeline is a compile error to think about rather than a silent no-op.
 */
public interface DialogOutcomeSink {

    /** The Asterisk channel this conversation belongs to — for logging. */
    String channelId();

    /**
     * The line to speak alongside the tool call.
     *
     * <p>Only the cascade pipeline has anything to do with this: there, a tool-calling
     * turn comes back with no text of its own, so the sentence rides in as a tool
     * argument (see {@link DialogTools}). A realtime engine speaks straight from audio
     * and has already said its line by the time the call arrives, so its implementation
     * ignores this.
     */
    void addToolReply(String reply);

    /** The scenario this call runs — its stages are what {@link #setState} moves between. */
    ScenarioDefinition scenario();

    /** The stage the conversation is in right now. */
    String state();

    /** Move the FSM to {@code state}. */
    void setState(String state);

    /** Record one named result of the conversation, written to {@code call_result} at teardown. */
    void recordOutcome(String key, Object value);

    /** Everything recorded so far this call — what a stage's {@code requiredOutcomes} are checked against. */
    Map<String, Object> outcome();

    /** Settle the disposition without ending the call — the conversation carries on. */
    void setDisposition(Disposition disposition);

    /** Settle the disposition and end the conversation. */
    void end(Disposition disposition);

    /** Why the client asked never to be called again (§11.4) — recorded against the number at teardown. */
    void setDoNotCallReason(String reason);

    /** Why the call is being handed to a person — what the operator is told before they take it. */
    void setTransferReason(String reason);

    /**
     * Press keypad tones on this call, so the bot can work its way through the automated
     * menu a company answers with before a person does.
     *
     * @return whether the tones were sent — {@code false} when this call has no way to send
     *         them (a simulated conversation, or a session started without an Asterisk channel),
     *         which the tool reports back to the model instead of pretending it pressed a button
     */
    boolean sendDtmf(String digits);

    /**
     * Counts one more call of a company tool on this conversation and returns the new count,
     * which is what {@code Tool.maxCallsPerCall} is checked against.
     */
    int countToolCall(String toolName);

    /**
     * The model judged the caller's words unfinished ("aa…", half a sentence) and chose to
     * say nothing and keep listening.
     */
    void waitForCaller();

    /**
     * The caller asked to be spoken to in another language and the model heard it.
     *
     * @return whether the call now runs in {@code language} — {@code false} when it is not
     *         one this company's calls are held in, or the pipeline cannot switch
     */
    boolean switchCallLanguage(String language);

    /**
     * The model's read of the caller's mood, which sets the voice and the empathy directive
     * from the next reply on.
     */
    void noteCallerMood(CustomerSentiment mood);

    /** The agent behind this call; {@code null} when none is (a manual test line, a dry run from the panel). */
    AiAgent agent();

    /** The facts this call started with; {@code null} for a sink that has none (a dry run from the panel). */
    CallContext context();

    /**
     * Figures a company tool's answer showed the model — prices, totals, order numbers —
     * which the fact guard then lets it say (UNIVERSAL-OPERATOR-PLAN A.3).
     */
    void recordToolFigures(Collection<BigDecimal> figures);

    /**
     * The write-tool call read back to the caller and waiting for their yes, or null
     * (UNIVERSAL-OPERATOR-PLAN B.3, {@link HttpToolExecutor}).
     */
    PendingToolConfirmation findPendingConfirmation(String toolName);

    /** Hold {@code pending} for {@code toolName}'s next call; null lets it go. */
    void setPendingConfirmation(String toolName, PendingToolConfirmation pending);

    /** How many times the caller has spoken so far — what tells a later answer from the same breath. */
    int countCallerUtterances();

    /** What the caller said last, or null before they have said anything. */
    String findLastCallerText();

    /** Keep the last catalog search's answer where the model will see it again ({@link CatalogTools}). */
    void rememberCatalogResult(String result);

    /**
     * Sends {@code text} by SMS to the number this call is with.
     *
     * @return whether the gateway took it — {@code false} when this call has no way to send
     *         one (mid-call SMS is off for its agent, or its pipeline wires no sender) or the
     *         send failed, which the tool reports back instead of claiming the SMS went
     */
    boolean triggerMidCallSms(String text);
}
