package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionAgentTransfer;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionPressDigit;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionSms;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsContentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsRecipient;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Tools the LLM can call to steer the call itself — move the FSM, hand over, hang up, change
 * language (PROJECT.md §4.2, MASTER_ROADMAP.md §5). Outcome tools (promise, reason, booking)
 * are the scenario's own, built by {@link ScenarioToolCallbackFactory}.
 */
public class DialogTools {

    private static final Logger log = LoggerFactory.getLogger(DialogTools.class);

    static final String REPLY_PARAM = "reply";
    static final String REPLY_DESCRIPTION =
            "fill this in only if you have NOT already written what you say to the client as plain text — "
                    + "if you have, leave it empty and do not repeat the sentence. "
                    + "English technical words or placeholders (e.g. dynamic_thought_or_fallback) are STRICTLY FORBIDDEN";

    /**
     * The tools that steer the call itself and so stay code. Every other tool a scenario
     * declares (promise, refusal reason, callback, booking…) is built generically by
     * {@link ScenarioToolCallbackFactory}, a name here is never built that way.
     */
    static final Set<String> HARDCODED_TOOL_NAMES = Set.of(
            "requestHumanTransfer", "recordWrongPerson", "recordDoNotCall", "endCall", "sendDtmfTones",
            "waitForCaller", "switchLanguage", "noteCallerMood", "noteClientFact"
    );

    /**
     * Whether a tool may hand the model something to answer with (an HTTP, MCP, CRM or
     * catalog tool) rather than steering the call. The cascade pipeline feeds such a
     * result back to the model in a follow-up round ({@link LlmTurnClient}).
     */
    static boolean isDataTool(String toolName) {
        return !HARDCODED_TOOL_NAMES.contains(toolName)
                && !"transitionTo".equals(toolName)
                && !"sendMidCallSms".equals(toolName);
    }

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
            "waitForCaller", "switchLanguage", "noteCallerMood", "noteClientFact"
    );

    /**
     * Whether the agent may press keypad digits on the line itself, without its scenario declaring
     * {@code sendDtmfTones}: its PRESS_DIGIT function when it saved a function list, else its press-digit setting.
     */
    static boolean isPressDigitEnabled(DialogOutcomeSink session) {
        return AgentFunctionPlan.offersPressDigit(session.agent());
    }

    /**
     * Always-available tools only the cascade pipeline offers: a realtime engine does its own
     * turn-taking, speaks whatever language it hears, and has no separate voice to set a mood on.
     */
    static final Set<String> CASCADE_ONLY_TOOL_NAMES =
            Set.of("waitForCaller", "switchLanguage", "noteCallerMood", "noteClientFact");

    /**
     * One SMS a call, counted by sendMidCallSms on the counter company tools use too —
     * hence a key no company tool is expected to be named.
     */
    static final String SMS_BUDGET_KEY = "sms:perCall";

    /** Three Latin-script segments: anything longer is an essay the model wrote, not an SMS. */
    static final int MAX_SMS_CHARS = 480;

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
        // A name that is no stage of the scenario would park the call in a state with no
        // prompt, tools or transitions; the scenario names no terminal state, calls end by endCall.
        ScenarioDefinition known = session.scenario();
        if (known != null && known.stages() != null && known.findStage(nextStage) == null) {
            log.warn("[{}] refused transition {} -> {}: no such stage", session.channelId(), session.state(), nextStage);
            return "ERROR: there is no stage " + nextStage + " in this conversation"
                    + (StageEntry.hasNowhereToTransition(known) ? ", it has a single stage"
                    : " — you can only move to " + String.join(", ", allowedTransitions()))
                    + ". Do not repeat what you have already said in this stage.";
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
        // An agent with a saved function list texts as its SEND_SMS function says; any other follows its
        // older midCallSmsTemplate.
        AgentFunctionSms sms = AgentFunctionPlan.findSms(session.agent()).orElse(null);
        boolean modelWrites = sms != null && sms.contentMode() == AgentSmsContentMode.PROMPT;
        String body;
        if (modelWrites) {
            // The function's content is the model's instruction, and the text it writes is the SMS.
            if (text == null || text.isBlank()) {
                return "ERROR: write the SMS text in the text parameter.";
            }
            body = text;
        } else {
            String template = sms != null ? sms.content() : midCallSmsTemplate();
            if (template == null) {
                // Without a template the model would write the whole message itself, in words
                // nobody reviewed — and a gateway delivers only registered templates anyway.
                return "ERROR: this agent has no SMS template, no SMS can be sent. "
                        + "Tell the client you cannot send an SMS right now.";
            }
            if (SMS_TEXT_SLOT.matcher(template).find() && (text == null || text.isBlank())) {
                return "ERROR: pass the link or code that goes into the SMS template in the text parameter.";
            }
            body = renderSmsTemplate(template, text, sms != null);
        }
        String number = null;
        if (sms != null && sms.sendTo() == AgentSmsRecipient.NUMBER) {
            number = AgentFunctionPlan.findSmsNumber(session);
            if (number == null) {
                return "ERROR: the SMS recipient is not known on this call, no SMS can be sent. "
                        + "Tell the client you cannot send an SMS right now.";
            }
        }
        String refusal = sendGuardedSms(body, number);
        if (refusal != null) {
            return refusal;
        }
        boolean silent = sms != null && !sms.speakAfter();
        if (!silent) {
            recordReply(reply);
        }
        session.recordOutcome("midCallSmsSent", true);
        session.recordOutcome("midCallSmsText", body.trim());
        log.info("[{}] mid-call SMS sent ({} chars)", session.channelId(), body.trim().length());
        return silent
                ? "SMS sent. Do not mention it to the client and write no reply text about it; carry on with the conversation."
                : "SMS sent to the client's phone. Tell the client the SMS has been sent.";
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
    private String renderSmsTemplate(String template, String text, boolean fromFunction) {
        Map<String, Object> values = new HashMap<>();
        CallContext context = session.context();
        if (fromFunction) {
            // A function's text may use what the call has recorded too (an extracted name, a tool's answer).
            values.putAll(StageEntry.variables(session));
        } else if (context != null && context.facts() != null) {
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
     * @param number the number a SEND_SMS function names instead of the call's own; null for the call's own
     * @return the refusal to hand back to the model, or {@code null} once the gateway took the SMS
     */
    private String sendGuardedSms(String text, String number) {
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
        boolean sent = number != null ? session.triggerSmsToNumber(body, number) : session.triggerMidCallSms(body);
        if (!sent) {
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

    /**
     * IVR navigation. Only reaches the model when the agent has press-digit switched on
     * ({@link #isPressDigitEnabled}) or the scenario declares a {@code ToolDef} named
     * {@code sendDtmfTones} — an outbound scenario that dials companies rather than
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
        pauseForMenu();
        return "Pressed '" + digits + "'. Now wait for the menu's answer.";
    }

    /**
     * The PRESS_DIGIT function's {@code pauseDetectionDelayMs}: the menu needs a moment to answer, so the tool
     * answers the model only after it. The tones themselves go out with fixed timings.
     */
    private void pauseForMenu() {
        Integer delay = AgentFunctionPlan.find(session.agent(), AgentFunctionType.PRESS_DIGIT)
                .map(AgentFunction::pressDigit).map(AgentFunctionPressDigit::pauseDetectionDelayMs).orElse(null);
        if (delay == null || delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Tool(description = "Hands the call over to another specialist agent of this company, who carries on the "
            + "same conversation. Use it only when the conversation needs what that agent does")
    public String transferToAgent(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                  @ToolParam(description = "why the call is handed over", required = false) String reason) {
        Long target = AgentFunctionPlan.find(session.agent(), AgentFunctionType.AGENT_TRANSFER)
                .map(AgentFunction::agentTransfer).map(AgentFunctionAgentTransfer::agentId).orElse(null);
        if (target == null || !session.handOffToAgent(target)) {
            log.warn("[{}] handoff to agent {} refused", session.channelId(), target);
            return "ERROR: the call could not be handed to the other agent. Carry on yourself "
                    + "and do not mention a transfer.";
        }
        recordReply(reply);
        log.info("[{}] call handed to agent {}: {}", session.channelId(), target, reason);
        return "The call is now handled by the other agent. Carry on in the new role, do not greet from scratch "
                + "and do not mention the handover.";
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
                                 @ToolParam(description = "language code of one of the languages allowed on this call, e.g. uz-UZ, ru-RU or en-US") String language) {
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

    @Tool(description = "Call when the client tells you something that will matter later in this call and that "
            + "no other tool records: their situation, a constraint, who decides, when they get paid (e.g. "
            + "situation = in hospital, salary_day = 15th). Short snake_case name, value of a few words. "
            + "Only NEW information, never a fact you were given; the same name again replaces the value. "
            + "Write what you say to the client as plain text as usual — this tool only remembers.")
    public String noteClientFact(@ToolParam(description = "short snake_case name of the fact") String name,
                                 @ToolParam(description = "the fact in a few words") String value) {
        if (!session.noteClientFact(name, value)) {
            return "Not noted: the name or value is empty, or enough facts are already noted.";
        }
        log.info("[{}] client fact noted: {}", session.channelId(), name);
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
}
