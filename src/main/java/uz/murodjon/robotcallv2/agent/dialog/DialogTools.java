package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Tools the LLM can call to drive the FSM and record outcomes (PROJECT.md §4.2, MASTER_ROADMAP.md §5).
 */
public class DialogTools {

    private static final Logger log = LoggerFactory.getLogger(DialogTools.class);

    static final String REPLY_PARAM = "reply";
    static final String REPLY_DESCRIPTION =
            "fill this in only if you have NOT already written what you say to the client as plain text — "
                    + "if you have, leave it empty and do not repeat the sentence. "
                    + "English technical words or placeholders (e.g. dynamic_thought_or_fallback) are STRICTLY FORBIDDEN";

    static final Set<String> HARDCODED_TOOL_NAMES = Set.of(
            "recordPaymentPromise", "recordRefusalReason", "scheduleCallback",
            "requestHumanTransfer", "recordWrongPerson", "recordDoNotCall",
            "sendSmsPaymentLink", "requestPaymentExtension", "recordPaymentClaim", "endCall", "sendDtmfTones",
            "bookShowroomVisit", "sendLocationSms", "sendCatalogSms", "waitForCaller", "switchLanguage", "noteCallerMood",
            "bookAppointment", "checkCalendarAvailability", "cancelAppointment"
    );

    /**
     * Tools every call gets whatever its scenario declares: the one that drives the FSM,
     * and the four ways out of a conversation that should not continue — hand the caller
     * to a person, mark the wrong person, blacklist the number, hang up.
     *
     * <p>A scenario cannot opt out of these, and both pipelines have to agree on that. A
     * model that is only given the outcome tools its scenario happens to declare reaches
     * for one of those when it should be reaching for an exit: on a recorded realtime
     * call the caller said "alo", and the only outcome tool on offer was
     * recordPaymentPromise.
     */
    static final Set<String> ALWAYS_AVAILABLE_TOOL_NAMES = Set.of(
            "transitionTo", "requestHumanTransfer", "recordWrongPerson", "recordDoNotCall", "endCall",
            "waitForCaller", "switchLanguage", "noteCallerMood"
    );

    /**
     * Always-available tools only the cascade pipeline offers: a realtime engine does its own
     * turn-taking, speaks whatever language it hears, and has no separate voice to set a mood on.
     */
    static final Set<String> CASCADE_ONLY_TOOL_NAMES = Set.of("waitForCaller", "switchLanguage", "noteCallerMood");

    /**
     * One SMS a call, counted across sendMidCallSms and sendSmsPaymentLink on the counter
     * company tools use too — hence a key no company tool is expected to be named.
     */
    static final String SMS_BUDGET_KEY = "sms:perCall";

    /** Three Latin-script segments: anything longer is an essay the model wrote, not an SMS. */
    static final int MAX_SMS_CHARS = 480;

    /** The call fact a payment link arrives under, from the contact's data or the CRM. */
    static final String PAYMENT_LINK_FACT = "paymentLink";

    private static final String PAYMENT_LINK_SMS_TEXT = "To'lov havolasi: ";

    /** The slot in {@code midCallSmsTemplate} the model's text goes into: {@code {{text}}}. */
    static final String SMS_TEXT_PLACEHOLDER = "text";
    private static final Pattern SMS_TEXT_SLOT = Pattern.compile("\\{\\{\\s*" + SMS_TEXT_PLACEHOLDER + "\\s*[|}]");

    private final DialogOutcomeSink session;
    /** Runs a CODE stage's script; null where no stage script may run (a CODE stage then sets nothing). */
    private final StageScriptRunner scripts;

    public DialogTools(DialogOutcomeSink session, StageScriptRunner scripts) {
        this.session = session;
        this.scripts = scripts;
    }

    public DialogTools(DialogOutcomeSink session) {
        this(session, null);
    }

    private void recordReply(String reply) {
        if (reply == null || reply.isBlank()) {
            // The normal case now: the line was written as plain text and streamed to
            // synthesis sentence by sentence, so the tool carries only the outcome
            // (SystemPromptFactory#turnAnnex). Nothing to record and nothing to warn about.
            return;
        }
        // Text carried in a tool argument never passed through LlmTurnClient.extractText, so this
        // is the second and last door model text comes in by.
        String speakable = SpeechSanitizer.stripUnspeakableCharacters(reply);
        if (!SpeechSanitizer.isUnspeakable(speakable)) {
            session.addToolReply(speakable);
        } else {
            log.warn("[{}] tool dropped unspeakable reply: '{}'", session.channelId(), reply);
        }
    }

    /**
     * The stages the scenario lets the current one move to — its own transitions, the global
     * stages and, in a FLEX flow, every conversation stage ({@link StageEntry#findReachableStages})
     * — or empty when it names none.
     *
     * <p>Checked rather than trusted: a jump past these is only taken along the scenario's
     * own graph ({@link #pathTo}), so a stage the call cannot reach from here is still
     * refused. The stages walked on the way are not verified as done — the prompt still
     * asks for each one to be served.
     */
    private List<String> allowedTransitions() {
        ScenarioDefinition scenario = session.scenario();
        return StageEntry.findReachableStages(scenario, scenario == null ? null : scenario.findStage(session.state()));
    }

    /**
     * The outcomes {@code stage} waits for when none of them is recorded yet, or empty when it
     * waits for none or one is in ({@link StageDef#requiredOutcomes} — any of, not all of).
     */
    private List<String> findUnmetRequiredOutcomes(StageDef stage) {
        if (stage == null || stage.requiredOutcomes() == null || stage.requiredOutcomes().isEmpty()) {
            return List.of();
        }
        Map<String, Object> recorded = session.outcome();
        for (String name : stage.requiredOutcomes()) {
            if (recorded.containsKey(name)) {
                return List.of();
            }
        }
        return stage.requiredOutcomes();
    }

    /**
     * The stages from the current one to {@code target} along the scenario's transitions,
     * the shortest way — {@code target} last, the current stage left out — or null when
     * the graph does not lead there.
     */
    private List<String> pathTo(String target) {
        ScenarioDefinition scenario = session.scenario();
        String start = session.state();
        if (scenario == null || start == null || target == null) {
            return null;
        }
        Map<String, String> cameFrom = new HashMap<>();
        cameFrom.put(start, null);
        Deque<String> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            StageDef stage = scenario.findStage(current);
            if (stage == null || stage.allowedTransitions() == null) {
                continue;
            }
            for (String next : stage.allowedTransitions()) {
                if (cameFrom.containsKey(next)) {
                    continue;
                }
                cameFrom.put(next, current);
                if (next.equals(target)) {
                    Deque<String> path = new ArrayDeque<>();
                    for (String at = target; !at.equals(start); at = cameFrom.get(at)) {
                        path.addFirst(at);
                    }
                    return new ArrayList<>(path);
                }
                queue.add(next);
            }
        }
        return null;
    }

    @Tool(description = "Moves the conversation to the next allowed stage")
    public String transitionTo(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                               @ToolParam(description = "id of the next stage") String nextStage) {
        if (nextStage != null && nextStage.trim().equalsIgnoreCase(session.state())) {
            recordReply(reply);
            return "State unchanged: stayed in the current stage " + session.state() + ".";
        }
        // A global stage ("the caller asks for a person") answers the caller, not the stage
        // they interrupted, so the stage's unfinished business does not hold it back.
        List<String> required = StageEntry.isGlobal(session.scenario(), nextStage) ? List.of()
                : findUnmetRequiredOutcomes(session.scenario() == null ? null : session.scenario().findStage(session.state()));
        if (!required.isEmpty()) {
            log.warn("[{}] refused transition {} -> {}: no required outcome {} recorded yet",
                    session.channelId(), session.state(), nextStage, required);
            return "ERROR: stage " + session.state() + " is not finished yet — first record the "
                    + String.join(" or ", required)
                    + " outcome (with the matching tool). If the client refuses to answer — endCall or "
                    + "requestHumanTransfer. Do not repeat what you have already said in this stage.";
        }
        List<String> allowed = allowedTransitions();
        List<String> walked = List.of();
        // A refused jump strands the call: on a recorded realtime call every jump was
        // refused, each refusal was heard as a repeat, the call never left GREETING, and the
        // caller's payment promise was refused along with it. The stages the graph leads
        // through are walked instead; a stage it does not lead to is still refused.
        boolean jump = !allowed.isEmpty() && !allowed.contains(nextStage);
        List<String> path = jump ? pathTo(nextStage) : null;
        if (path != null) {
            // A stage with branches is a check the walk must not skip — the identity check
            // before the debt is named. The walk stops there and the branches decide. A stage
            // that still waits for its required outcome is the same kind of check.
            for (int i = 0; i < path.size() - 1; i++) {
                StageDef between = session.scenario().findStage(path.get(i));
                boolean branching = between != null && between.branches() != null && !between.branches().isEmpty();
                if (branching || !findUnmetRequiredOutcomes(between).isEmpty()) {
                    path = path.subList(0, i + 1);
                    nextStage = path.get(i);
                    break;
                }
            }
            walked = path.subList(0, path.size() - 1);
            log.warn("[{}] transition {} -> {} walked the skipped stages {}",
                    session.channelId(), session.state(), nextStage, walked);
        } else if (jump) {
            log.warn("[{}] refused transition {} -> {}", session.channelId(), session.state(), nextStage);
            // The last sentence is the safety net for a realtime call, where the model may
            // already be speaking when the refusal lands: without it, stepping into the
            // stage it had just voiced made it voice the stage all over again.
            return "ERROR: from stage " + session.state() + " you can only move to " + String.join(", ", allowed)
                    + ". Do not skip stages. "
                    + "Do not repeat what you have already said in this stage.";
        }
        recordReply(reply);
        session.setState(nextStage);
        log.info("[{}] dialog state -> {}", session.channelId(), nextStage);
        String landed = StageEntry.enter(session, nextStage, scripts);
        // Where it may go from here, in the answer. The cascade path re-states this every
        // turn in the prompt annex; a realtime call has no annex, so this tool result is
        // the only place the model is told again — and on a recorded call it spent three
        // turns guessing stage names it was not allowed to reach.
        ScenarioDefinition scenario = session.scenario();
        StageDef landedStage = scenario == null ? null : scenario.findStage(landed);
        String next = SystemPromptFactory.describeReachableStages(scenario, landedStage);
        String moved = "Moved to stage " + landed
                + (landed.equals(nextStage) ? "" : " (automatically, by the stages after " + nextStage + ")")
                + (walked.isEmpty() ? "" : " (stages walked through: " + String.join(", ", walked) + ")");
        // A stage whose job is the model's: on a realtime call this result is how it learns
        // what to do now; the cascade pipeline asks for it in a turn of its own (TurnRunner).
        String task = StageEntry.needsModelAction(landedStage) && !session.isEnded()
                ? " " + StageEntry.describeTask(scenario, landedStage, 1, StageEntry.variables(session))
                : "";
        return (next.isEmpty()
                ? moved
                : moved + ". From this stage you can only move to " + next + ".") + task;
    }

    @Tool(description = "Call when the client names an EXACT DAY themselves (e.g. \"10-oktabr\", \"ertaga\"). "
            + "Do NOT call it on a vague answer like \"keyingi oy\", \"oy oxirida\", \"pul bo'lganda\" "
            + "— first ask which day. Guessing the date yourself is FORBIDDEN")
    public String recordPaymentPromise(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "promised date, format yyyy-MM-dd") LocalDate promisedDate,
            @ToolParam(description = "promised amount", required = false) BigDecimal amount,
            @ToolParam(description = "payment channel the client named: CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL "
                    + "or OTHER. Leave empty if the client did not say", required = false) String paymentChannel,
            @ToolParam(description = "additional note", required = false) String note) {
        if (promisedDate != null && promisedDate.isBefore(LocalDate.now())) {
            return "ERROR: " + promisedDate + " is in the past. Today is " + LocalDate.now()
                    + ". Work the date out from today and send it again, or ask the client for the exact date.";
        }
        recordReply(reply);
        session.recordOutcome("promisedDate", promisedDate);
        session.recordOutcome("promisedAmount", amount);
        session.recordOutcome("paymentChannel", paymentChannel);
        session.setDisposition(Disposition.PROMISE_TO_PAY);
        log.info("[{}] payment promise: date={}, amount={}, channel={}, note={}",
                session.channelId(), promisedDate, amount, paymentChannel, note);
        return "Payment promise recorded: " + promisedDate;
    }

    @Tool(description = "Records the reason when the client says they cannot pay")
    public String recordRefusalReason(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "reason for not paying") String reason,
            @ToolParam(description = "details of the reason", required = false) String detail) {
        recordReply(reply);
        session.recordOutcome("reasonCode", reason);
        session.setDisposition(Disposition.REFUSED);
        log.info("[{}] refusal reason: {} ({})", session.channelId(), reason, detail);
        return "Reason recorded: " + reason;
    }

    @Tool(description = "Call when the client asks to be called back later (e.g. tomorrow or at 16:00)")
    public String scheduleCallback(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "callback date and time (e.g. 2026-09-02T16:00:00 or 'ertaga soat 16:00')") String callbackTime,
            @ToolParam(description = "reason or note", required = false) String reason) {
        recordReply(reply);
        session.recordOutcome("callbackTime", callbackTime);
        session.recordOutcome("callbackReason", reason);
        session.setDisposition(Disposition.CALLBACK_REQUESTED);
        log.info("[{}] callback requested: time={}, reason={}", session.channelId(), callbackTime, reason);
        return "Callback scheduled: " + callbackTime;
    }

    @Tool(description = "Hands the call to an operator only when the client explicitly asks for a person "
            + "or an operator. Rudeness or swearing alone is not a reason to transfer")
    public String requestHumanTransfer(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                       @ToolParam(description = "reason for the transfer") String reason) {
        String finalReply = reply;
        if ((finalReply == null || finalReply.isBlank()) && session.agent() != null
                && session.agent().callBehaviour().transferMessage() != null && !session.agent().callBehaviour().transferMessage().isBlank()) {
            finalReply = session.agent().callBehaviour().transferMessage();
        }
        recordReply(finalReply);
        session.setTransferReason(reason);
        session.end(Disposition.TRANSFERRED);
        log.info("[{}] human transfer requested: {}", session.channelId(), reason);
        return "Transfer to an operator requested. Say goodbye to the client.";
    }

    @Tool(description = "Sends the client an SMS message, link or code during the call. "
            + "Only to this client's number, once per call, at most " + MAX_SMS_CHARS + " characters")
    public String sendMidCallSms(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                 @ToolParam(description = "SMS text or link to send. If the agent has an SMS template "
                                         + "— not the whole message, only the part that goes into the template (a link or a code)",
                                         required = false) String text) {
        String template = midCallSmsTemplate();
        if (template == null) {
            // Without a template the model would write the whole message itself, in words
            // nobody reviewed — and a gateway delivers only registered templates anyway.
            return "ERROR: this agent has no SMS template, no SMS can be sent. "
                    + "Tell the client you cannot send an SMS right now.";
        }
        if (SMS_TEXT_SLOT.matcher(template).find() && (text == null || text.isBlank())) {
            return "ERROR: pass the link or code that goes into the SMS template in the text parameter.";
        }
        String body = renderSmsTemplate(template, text);
        String refusal = sendGuardedSms(body);
        if (refusal != null) {
            return refusal;
        }
        recordReply(reply);
        session.recordOutcome("midCallSmsSent", true);
        session.recordOutcome("midCallSmsText", body.trim());
        log.info("[{}] mid-call SMS sent ({} chars)", session.channelId(), body.trim().length());
        return "SMS sent to the client's phone. Tell the client the SMS has been sent.";
    }

    /** The agent's {@code midCallSmsTemplate}, or null when it has none — and then no mid-call SMS is sent. */
    private String midCallSmsTemplate() {
        String template = session.agent() != null ? session.agent().callBehaviour().midCallSmsTemplate() : null;
        return template == null || template.isBlank() ? null : template.trim();
    }

    /**
     * The SMS a template makes. SMS gateways deliver only texts matching a template
     * registered with them (Eskiz, in production), so with a template the model no longer
     * writes the message: it fills the template's {@code {{text}}} slot — a link, a code —
     * and the call's facts fill the rest ({@code {{debtAmount}}}). A template without the
     * slot is sent as it stands, whatever the model wrote.
     */
    private String renderSmsTemplate(String template, String text) {
        Map<String, Object> values = new HashMap<>();
        CallContext context = session.context();
        if (context != null && context.facts() != null) {
            values.putAll(context.facts());
        }
        values.put(SMS_TEXT_PLACEHOLDER, text == null ? "" : text.trim());
        return PromptTemplateEngine.render(template, values);
    }

    /**
     * Every SMS the model asks for goes through here. The number is not a parameter at all:
     * the session sends to the number this call is with, so no argument can address anyone
     * else. The checks run in the order that keeps a malformed request from spending the
     * call's one SMS; a send that failed does spend it — a timed-out request may still have
     * been delivered, and a retry would be the customer's second copy.
     *
     * @return the refusal to hand back to the model, or {@code null} once the gateway took the SMS
     */
    private String sendGuardedSms(String text) {
        if (text == null || text.isBlank()) {
            return "ERROR: the SMS text is empty. Write the text to send.";
        }
        String body = text.trim();
        if (body.length() > MAX_SMS_CHARS) {
            return "ERROR: the SMS text is " + body.length() + " characters, at most " + MAX_SMS_CHARS
                    + " are allowed. Call again with a shorter text.";
        }
        if (session.countToolCall(SMS_BUDGET_KEY) > 1) {
            return "ERROR: an SMS has already been sent on this call, a second one is not sent. "
                    + "Tell the client to look at the earlier SMS.";
        }
        if (!session.triggerMidCallSms(body)) {
            log.warn("[{}] SMS not sent ({} chars)", session.channelId(), body.length());
            return "ERROR: the SMS was not sent. Do not tell the client it was sent; tell them you cannot send it right now.";
        }
        return null;
    }

    @Tool(description = "Call when it turns out the person who answered is not the person being called")
    public String recordWrongPerson(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                    @ToolParam(description = "details") String detail) {
        recordReply(reply);
        session.end(Disposition.WRONG_NUMBER);
        log.info("[{}] wrong person: {}", session.channelId(), detail);
        return "Marked as the wrong person. Apologise and say goodbye.";
    }

    @Tool(description = "Call when the client firmly demands not to be called again or to have their number removed")
    public String recordDoNotCall(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                  @ToolParam(description = "reason for the demand") String reason) {
        recordReply(reply);
        session.setDoNotCallReason(reason);
        session.end(Disposition.DO_NOT_CALL);
        log.info("[{}] do not call registered: reason={}", session.channelId(), reason);
        return "The number has been added to the do-not-call list. Apologise briefly and say goodbye.";
    }

    @Tool(description = "Call when the client asks for the payment link or payment details by SMS")
    public String sendSmsPaymentLink(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                     @ToolParam(description = "SMS message type or additional text", required = false) String note) {
        session.recordOutcome("sendSmsRequested", true);
        session.recordOutcome("smsNote", note);
        String link = findPaymentLink(session.context());
        if (link == null) {
            // No link on this call to send from here: the request stays recorded for whoever
            // follows up after the call, as it always was.
            recordReply(reply);
            log.info("[{}] SMS payment link requested, none on this call", session.channelId());
            return "The payment link will be sent by SMS.";
        }
        // The model decides when, never what: the text is fixed and the link is the call's own.
        String refusal = sendGuardedSms(PAYMENT_LINK_SMS_TEXT + link);
        if (refusal != null) {
            return refusal;
        }
        recordReply(reply);
        session.recordOutcome("paymentLinkSmsSent", true);
        log.info("[{}] payment link SMS sent", session.channelId());
        return "The payment link was sent to the client's phone by SMS. Ask the client to check their SMS.";
    }

    /**
     * The call's payment link, when its {@value #PAYMENT_LINK_FACT} fact is a whole web
     * address. A fact longer than {@link CallContext} keeps arrives cut and ending in "…",
     * and half a link is worse than none.
     */
    private static String findPaymentLink(CallContext context) {
        return findLink(context, null, PAYMENT_LINK_FACT);
    }

    @Tool(description = "Call when the client asks to extend (postpone) the payment deadline by a few days")
    public String requestPaymentExtension(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                          @ToolParam(description = "how many days the extension is for") int extensionDays,
                                          @ToolParam(description = "reason for the delay") String reason) {
        recordReply(reply);
        session.recordOutcome("extensionRequestedDays", extensionDays);
        session.recordOutcome("extensionReason", reason);
        log.info("[{}] payment extension requested: {} days, reason: {}", session.channelId(), extensionDays, reason);
        return "Payment extension request recorded: " + extensionDays + " days.";
    }

    /**
     * "I already paid." Not argued with and not a refusal: the claim is filed, and
     * {@link Disposition#PAYMENT_CLAIMED} keeps the dialer off the number for three days
     * while the company checks it ({@code RetrySchedule}).
     */
    @Tool(description = "Call when the client says they have ALREADY paid the debt. First ask when and through "
            + "which payment system they paid. Do not argue with the client: tell them they will not be called "
            + "until the payment has been checked")
    public String recordPaymentClaim(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                     @ToolParam(description = "payment date the client named, format yyyy-MM-dd",
                                             required = false) LocalDate paidDate,
                                     @ToolParam(description = "payment channel: CLICK, PAYME, UZUM, BANK_BRANCH, "
                                             + "TERMINAL or OTHER", required = false) String paymentChannel) {
        if (paidDate != null && paidDate.isAfter(LocalDate.now())) {
            return "ERROR: " + paidDate + " is in the future. Today is " + LocalDate.now()
                    + ". A payment date is today or earlier — clarify it with the client.";
        }
        recordReply(reply);
        session.recordOutcome("paidDate", paidDate);
        session.recordOutcome("paymentChannel", paymentChannel);
        session.setDisposition(Disposition.PAYMENT_CLAIMED);
        log.info("[{}] payment claimed: date={}, channel={}", session.channelId(), paidDate, paymentChannel);
        return "Payment claim recorded. The client will not be called for 3 days while it is checked.";
    }

    /**
     * IVR navigation. Only reaches the model when the scenario declares a {@code ToolDef}
     * named {@code sendDtmfTones} — an outbound scenario that dials companies rather than
     * people (see {@code TurnTools#build}). The scenario's own {@code ToolDef.description}
     * is not used here: the wording below is what stops the model asking the human to press
     * the key, or guessing a digit it has not heard the menu name.
     */
    @Tool(description = "When an automated menu (IVR) is speaking on the other end, the BOT presses the key itself. "
            + "Listen to the menu to the end or until the option you need is named, then call this tool with "
            + "only the matching digit. Do NOT ask the person to press a key. If it is not clear which digit, "
            + "do not call it — keep listening.")
    public String sendDtmfTones(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                @ToolParam(description = "keys to press: 0-9, * or #. Usually a single digit") String digits,
                                @ToolParam(description = "the words the menu used for this digit", required = false) String menuOption) {
        recordReply(reply);
        if (!session.sendDtmf(digits)) {
            log.warn("[{}] sendDtmfTones('{}') on a call with no keypad", session.channelId(), digits);
            return "ERROR: keys cannot be pressed on this call. Do not wait for the menu; keep talking.";
        }
        log.info("[{}] IVR: pressed '{}' for '{}'", session.channelId(), digits, menuOption);
        return "Pressed '" + digits + "'. Now wait for the menu's answer.";
    }

    @Tool(description = "Call when the client has not finished speaking: they are thinking, made only a "
            + "thinking sound, or their sentence broke off and is clearly to be continued. In that case "
            + "say nothing and write no text — the bot stays silent and waits for the client to go on, "
            + "and the rest arrives together with this sentence. If the client did not hear the question "
            + "or asked you to repeat it, this is NOT the tool — briefly repeat your last question.")
    public String waitForCaller() {
        session.waitForCaller();
        log.info("[{}] model chose to keep listening", session.channelId());
        return "Stay silent and wait for the client to go on.";
    }

    @Tool(description = "Call when the client asks to speak another language (in whatever words). "
            + "Do NOT write plain text alongside this tool — write your line in the new language in the "
            + "reply parameter; it is spoken in the new language's voice.")
    public String switchLanguage(@ToolParam(description = "the line to say to the client in the new language") String reply,
                                 @ToolParam(description = "language code: uz-UZ, ru-RU or en-US") String language) {
        if (!session.switchCallLanguage(language)) {
            log.info("[{}] language switch to {} refused — not one of this company's languages",
                    session.channelId(), language);
            return "ERROR: this language is not supported on this call. Apologise to the client and "
                    + "continue in the current language.";
        }
        // After the switch, so the line is synthesized in the new language's voice.
        recordReply(reply);
        log.info("[{}] caller asked for {} — language switched", session.channelId(), language);
        return "Language switched to " + language + ". From now on speak only this language.";
    }

    @Tool(description = "Call when you notice the client's mood has CHANGED: upset "
            + "(FRUSTRATED), lost track of what this is about (CONFUSED), pleased (POSITIVE) or calm "
            + "again (NEUTRAL). Not every turn — only when it changes. Write what you say to the client "
            + "as plain text as usual; this tool only adjusts the voice's tone and the style of the next reply.")
    public String noteCallerMood(@ToolParam(description = "the client's current mood") CustomerSentiment mood) {
        session.noteCallerMood(mood);
        log.info("[{}] caller mood noted: {}", session.channelId(), mood);
        return "Noted.";
    }

    @Tool(description = "Ends the conversation and records its outcome")
    public String endCall(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                          @ToolParam(description = "why the call ends / its outcome") Disposition disposition) {
        recordReply(reply);
        session.end(disposition);
        log.info("[{}] call ended with disposition: {}", session.channelId(), disposition);
        return "Call ended: " + disposition;
    }

    @Tool(description = "Books a meeting when the client agrees to come to the real-estate sales office (showroom). "
            + "Records the meeting time, the number of rooms they are interested in and the payment method. "
            + "Call only when the client clearly agrees to visit.")
    public String bookShowroomVisit(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "meeting date and time (e.g. '2026-09-20 15:00' or 'ertaga soat 15:00')") String visitTime,
            @ToolParam(description = "number of rooms needed (e.g. '2 xonali', '3 xonali')", required = false) String roomsNeeded,
            @ToolParam(description = "approximate budget", required = false) String budgetRange,
            @ToolParam(description = "payment method (100% payment, instalments, mortgage)", required = false) String paymentMethod,
            @ToolParam(description = "additional note", required = false) String note) {
        if (visitTime == null || visitTime.isBlank()) {
            return "ERROR: no meeting time given. Ask the client which day and hour they can come.";
        }
        recordReply(reply);
        session.recordOutcome("meetingBooked", true);
        session.recordOutcome("meetingTime", visitTime.trim());
        recordIfPresent("roomsNeeded", roomsNeeded);
        recordIfPresent("budgetRange", budgetRange);
        recordIfPresent("paymentMethod", paymentMethod);
        recordIfPresent("meetingNote", note);
        session.setDisposition(Disposition.GOAL_REACHED);
        log.info("[{}] showroom visit booked: time={}, rooms={}, budget={}, payment={}",
                session.channelId(), visitTime, roomsNeeded, budgetRange, paymentMethod);
        return "Sales office meeting booked: " + visitTime.trim()
                + ". Tell the client our specialists will meet them and offer to send the address/location by SMS.";
    }

    @Tool(description = "Sends the sales office (showroom) location and address by SMS when the client asks "
            + "for it, or after a meeting has been booked.")
    public String sendLocationSms(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "location link (Google Maps or Yandex Maps)", required = false) String locationUrl,
            @ToolParam(description = "office address or landmark", required = false) String address) {
        session.recordOutcome("locationSmsRequested", true);
        String link = findLink(session.context(), locationUrl, "locationLink", "showroomLocation", "mapUrl");
        String officeAddress = address != null && !address.isBlank()
                ? address.trim()
                : findLocationAddress(session.context());

        if (link == null && officeAddress == null) {
            recordReply(reply);
            log.info("[{}] location SMS requested but no location link/address found", session.channelId());
            return "The location details have been noted to be sent by SMS.";
        }

        StringBuilder smsText = new StringBuilder("Sotuv ofisimiz manzili: ");
        if (officeAddress != null) {
            smsText.append(officeAddress);
            if (link != null) smsText.append(". ");
        }
        if (link != null) {
            smsText.append("Lokatsiya: ").append(link);
        }

        String refusal = sendGuardedSms(smsText.toString());
        if (refusal != null) {
            return refusal;
        }
        recordReply(reply);
        session.recordOutcome("locationSmsSent", true);
        log.info("[{}] location SMS sent successfully", session.channelId());
        return "The sales office location was sent to the client's phone by SMS. Ask the client to check their SMS.";
    }

    @Tool(description = "Sends the catalogue link by SMS when the client asks for the residential complex's "
            + "projects, floor plans or presentation (catalogue).")
    public String sendCatalogSms(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "catalogue or presentation link", required = false) String catalogUrl) {
        session.recordOutcome("catalogSmsRequested", true);
        String link = findLink(session.context(), catalogUrl, "catalogUrl", "presentationUrl");
        if (link == null) {
            recordReply(reply);
            log.info("[{}] catalog SMS requested but no link found", session.channelId());
            return "The catalogue link has been noted to be sent by SMS.";
        }
        String refusal = sendGuardedSms("Loyihalarimiz katalogi va rejalari: " + link);
        if (refusal != null) {
            return refusal;
        }
        recordReply(reply);
        session.recordOutcome("catalogSmsSent", true);
        log.info("[{}] catalog SMS sent successfully", session.channelId());
        return "The catalogue link was sent to the client by SMS. Let the client know the SMS has arrived.";
    }

    @Tool(description = "Call when the client agrees to book a service, consultation or appointment. "
            + "Records the appointment date, time, the client's name and the service type, and marks the goal as reached.")
    public String bookAppointment(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "appointment date (e.g. '2026-10-15' or 'ertaga')") String appointmentDate,
            @ToolParam(description = "appointment time (e.g. '14:00' or '15:30')") String appointmentTime,
            @ToolParam(description = "client's name", required = false) String attendeeName,
            @ToolParam(description = "service or consultation type", required = false) String serviceType,
            @ToolParam(description = "additional note or requirements", required = false) String note) {
        if (appointmentDate == null || appointmentDate.isBlank()) {
            return "ERROR: no appointment date given. Ask the client which day they want to book.";
        }
        if (appointmentTime == null || appointmentTime.isBlank()) {
            return "ERROR: no appointment time given. Ask the client which hour they want to book.";
        }
        recordReply(reply);
        session.recordOutcome("appointmentBooked", true);
        session.recordOutcome("appointmentDate", appointmentDate.trim());
        session.recordOutcome("appointmentTime", appointmentTime.trim());
        recordIfPresent("appointmentAttendee", attendeeName);
        recordIfPresent("appointmentService", serviceType);
        recordIfPresent("appointmentNote", note);
        session.setDisposition(Disposition.GOAL_REACHED);
        log.info("[{}] appointment booked: date={}, time={}, attendee={}, service={}",
                session.channelId(), appointmentDate, appointmentTime, attendeeName, serviceType);
        return "Appointment booked: " + appointmentDate.trim() + " at " + appointmentTime.trim()
                + ". Confirm the appointment to the client and say you look forward to seeing them.";
    }

    @Tool(description = "Checks and returns the free slots in the calendar when the client asks for available appointment times.")
    public String checkCalendarAvailability(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "date being checked (e.g. '2026-10-15' or 'bugun', 'ertaga')") String date,
            @ToolParam(description = "service type", required = false) String serviceType) {
        if (date == null || date.isBlank()) {
            return "ERROR: no date given. Ask the client which date they want free times for.";
        }
        recordReply(reply);
        Object customSlots = session.context() != null ? session.context().fact("availableSlots") : null;
        String slots = customSlots instanceof String s && !s.isBlank() ? s : "10:00, 11:30, 14:00, 16:30";
        log.info("[{}] checked calendar availability for date={}: slots={}", session.channelId(), date, slots);
        return "Free times on " + date.trim() + ": " + slots
                + ". Offer the client one of these times.";
    }

    @Tool(description = "Call when the client asks to cancel an appointment booked earlier.")
    public String cancelAppointment(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "date of the appointment being cancelled", required = false) String appointmentDate,
            @ToolParam(description = "time of the appointment being cancelled", required = false) String appointmentTime,
            @ToolParam(description = "reason for cancelling", required = false) String reason) {
        recordReply(reply);
        session.recordOutcome("appointmentCancelled", true);
        recordIfPresent("cancelledAppointmentDate", appointmentDate);
        recordIfPresent("cancelledAppointmentTime", appointmentTime);
        recordIfPresent("cancellationReason", reason);
        log.info("[{}] appointment cancelled: date={}, time={}, reason={}",
                session.channelId(), appointmentDate, appointmentTime, reason);
        return "Appointment cancelled. Politely let the client know the appointment has been cancelled.";
    }

    private void recordIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            session.recordOutcome(key, value.trim());
        }
    }

    /**
     * {@code provided} when the model passed a whole web address, else the first of the
     * call's {@code factKeys} that is set — when that one is a whole web address.
     */
    private static String findLink(CallContext context, String provided, String... factKeys) {
        if (isValidWebLink(provided)) {
            return provided.trim();
        }
        return firstFact(context, factKeys) instanceof String s && isValidWebLink(s) ? s.trim() : null;
    }

    private static String findLocationAddress(CallContext context) {
        return firstFact(context, "showroomAddress", "officeAddress") instanceof String s && !s.isBlank()
                ? s.trim() : null;
    }

    private static Object firstFact(CallContext context, String... keys) {
        if (context == null) {
            return null;
        }
        for (String key : keys) {
            Object value = context.fact(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** A whole web address. A fact longer than {@link CallContext} keeps arrives cut and ending in "…". */
    private static boolean isValidWebLink(String value) {
        if (value == null) return false;
        String link = value.trim();
        return (link.startsWith("https://") || link.startsWith("http://"))
                && !link.endsWith("…")
                && link.chars().noneMatch(Character::isWhitespace);
    }
}
