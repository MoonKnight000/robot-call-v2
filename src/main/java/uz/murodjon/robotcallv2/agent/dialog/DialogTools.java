package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.service.StageBranchEvaluator;
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
            "faqat mijozga aytadigan gapni oddiy matn qilib YOZMAGAN bo'lsangiz to'ldiring — "
                    + "matnni allaqachon yozgan bo'lsangiz bu parametrni bo'sh qoldiring, gapni takrorlamang. "
                    + "Inglizcha texnik so'zlar yoki placeholderlar (masalan dynamic_thought_or_fallback) yozish QAT'IYAN TAQIQLANADI";

    static final Set<String> HARDCODED_TOOL_NAMES = Set.of(
            "recordPaymentPromise", "recordRefusalReason", "scheduleCallback",
            "requestHumanTransfer", "recordWrongPerson", "recordDoNotCall",
            "sendSmsPaymentLink", "requestPaymentExtension", "recordPaymentClaim", "endCall", "sendDtmfTones",
            "bookShowroomVisit", "sendLocationSms", "sendCatalogSms", "waitForCaller", "switchLanguage", "noteCallerMood"
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

    public DialogTools(DialogOutcomeSink session) {
        this.session = session;
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
     * Takes the stage's branches ({@code StageBranch}) on from {@code entered}, without the
     * model, until a stage has none that holds. Bounded by the number of stages: branches
     * that send the call round in a circle stop where the circle closes.
     *
     * @return the stage the call ended up in
     */
    private String followBranches(String entered) {
        ScenarioDefinition scenario = session.scenario();
        CallContext context = session.context();
        if (scenario == null || scenario.stages() == null || context == null) {
            return entered;
        }
        String current = entered;
        for (int hop = 0; hop < scenario.stages().size(); hop++) {
            StageDef stage = scenario.findStage(current);
            String target = stage == null ? null
                    : StageBranchEvaluator.findTarget(stage.branches(), context::fact);
            if (target == null) {
                break;
            }
            session.setState(target);
            log.info("[{}] dialog state -> {} (branch of {})", session.channelId(), target, current);
            current = target;
        }
        return current;
    }

    /**
     * The stages the scenario lets the current one move to, or empty when it names none.
     *
     * <p>Checked rather than trusted: a jump past these is only taken along the scenario's
     * own graph ({@link #pathTo}), so a stage the call cannot reach from here is still
     * refused. The stages walked on the way are not verified as done — the prompt still
     * asks for each one to be served.
     */
    private List<String> allowedTransitions() {
        StageDef stage = session.scenario() == null ? null : session.scenario().findStage(session.state());
        List<String> allowed = stage == null ? null : stage.allowedTransitions();
        return allowed == null ? List.of() : allowed;
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

    @Tool(description = "Suhbat bosqichini keyingi ruxsat etilgan holatga o'tkazadi")
    public String transitionTo(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                               @ToolParam(description = "keyingi bosqich id'si") String nextStage) {
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
            // before the debt is named. The walk stops there and the branches decide.
            for (int i = 0; i < path.size() - 1; i++) {
                StageDef between = session.scenario().findStage(path.get(i));
                if (between != null && between.branches() != null && !between.branches().isEmpty()) {
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
            return "XATO: " + session.state() + " bosqichidan faqat " + String.join(", ", allowed)
                    + " ga o'tish mumkin. Bosqichni tashlab ketmang. "
                    + "Bu bosqichda aytib bo'lgan gapingizni qayta aytmang.";
        }
        recordReply(reply);
        session.setState(nextStage);
        log.info("[{}] dialog state -> {}", session.channelId(), nextStage);
        String landed = followBranches(nextStage);
        // Where it may go from here, in the answer. The cascade path re-states this every
        // turn in the prompt annex; a realtime call has no annex, so this tool result is
        // the only place the model is told again — and on a recorded call it spent three
        // turns guessing stage names it was not allowed to reach.
        List<String> next = allowedTransitions();
        String moved = "Holat " + landed + " ga o'tkazildi"
                + (landed.equals(nextStage) ? "" : " (" + nextStage + " shartlari bo'yicha avtomatik)")
                + (walked.isEmpty() ? "" : " (oraliq bosqichlar: " + String.join(", ", walked) + ")");
        return next.isEmpty()
                ? moved
                : moved + ". Bu bosqichdan keyin faqat " + String.join(", ", next) + " ga o'tish mumkin.";
    }

    @Tool(description = "Mijoz ANIQ KUNni o'zi aytganda chaqiriladi (masalan \"10-oktabr\", \"ertaga\"). "
            + "Mijoz \"keyingi oy\", \"oy oxirida\", \"pul bo'lganda\" kabi noaniq javob bersa CHAQIRMANG "
            + "— avval qaysi kun ekanini so'rang. Sanani o'zingizdan taxmin qilish TAQIQLANADI")
    public String recordPaymentPromise(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "va'da qilingan sana, format yyyy-MM-dd") LocalDate promisedDate,
            @ToolParam(description = "va'da qilingan summa", required = false) BigDecimal amount,
            @ToolParam(description = "mijoz aytgan to'lov kanali: CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL "
                    + "yoki OTHER. Mijoz aytmagan bo'lsa bo'sh qoldiring", required = false) String paymentChannel,
            @ToolParam(description = "qo'shimcha izoh", required = false) String note) {
        if (promisedDate != null && promisedDate.isBefore(LocalDate.now())) {
            return "XATO: " + promisedDate + " o'tmishda. Bugun " + LocalDate.now()
                    + ". Sanani shundan hisoblab qaytadan yuboring yoki mijozdan aniq sanani so'rang.";
        }
        recordReply(reply);
        session.recordOutcome("promisedDate", promisedDate);
        session.recordOutcome("promisedAmount", amount);
        session.recordOutcome("paymentChannel", paymentChannel);
        session.setDisposition(Disposition.PROMISE_TO_PAY);
        log.info("[{}] payment promise: date={}, amount={}, channel={}, note={}",
                session.channelId(), promisedDate, amount, paymentChannel, note);
        return "To'lov va'dasi yozib olindi: " + promisedDate;
    }

    @Tool(description = "Mijoz to'lay olmasligini aytganda sababni yozib qo'yadi")
    public String recordRefusalReason(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "to'lamaslik sababi") String reason,
            @ToolParam(description = "sabab tafsiloti", required = false) String detail) {
        recordReply(reply);
        session.recordOutcome("reasonCode", reason);
        session.setDisposition(Disposition.REFUSED);
        log.info("[{}] refusal reason: {} ({})", session.channelId(), reason, detail);
        return "Sabab yozib olindi: " + reason;
    }

    @Tool(description = "Mijoz keyinroq (masalan ertaga yoki soat 16:00 da) qayta qo'ng'iroq qilishni so'raganda chaqiriladi")
    public String scheduleCallback(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "qayta qo'ng'iroq sanasi va vaqti (masalan 2026-09-02T16:00:00 yoki ertaga soat 16:00)") String callbackTime,
            @ToolParam(description = "sababi yoki izoh", required = false) String reason) {
        recordReply(reply);
        session.recordOutcome("callbackTime", callbackTime);
        session.recordOutcome("callbackReason", reason);
        session.setDisposition(Disposition.CALLBACK_REQUESTED);
        log.info("[{}] callback requested: time={}, reason={}", session.channelId(), callbackTime, reason);
        return "Qayta qo'ng'iroq rejalashtirildi: " + callbackTime;
    }

    @Tool(description = "Mijoz operator bilan gaplashishni so'raganda yoki janjal qilganda operatorga o'tkazadi")
    public String requestHumanTransfer(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                       @ToolParam(description = "o'tkazish sababi") String reason) {
        String finalReply = reply;
        if ((finalReply == null || finalReply.isBlank()) && session.agent() != null
                && session.agent().callBehaviour().transferMessage() != null && !session.agent().callBehaviour().transferMessage().isBlank()) {
            finalReply = session.agent().callBehaviour().transferMessage();
        }
        recordReply(finalReply);
        session.setTransferReason(reason);
        session.end(Disposition.TRANSFERRED);
        log.info("[{}] human transfer requested: {}", session.channelId(), reason);
        return "Operatorga o'tkazish so'raldi. Mijoz bilan xayrlashing.";
    }

    @Tool(description = "Mijozga suhbat davomida real-vaqtda SMS xabar, havola yoki kod yuboradi. "
            + "Faqat shu mijozning raqamiga, qo'ng'iroq davomida bir marta, ko'pi bilan " + MAX_SMS_CHARS + " belgi")
    public String sendMidCallSms(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                 @ToolParam(description = "Yuboriladigan SMS matni yoki havola. Agentning SMS shabloni "
                                         + "bo'lsa — butun xabar emas, faqat shablonga qo'yiladigan qism (havola yoki kod)",
                                         required = false) String text) {
        String template = midCallSmsTemplate();
        if (template == null) {
            // Without a template the model would write the whole message itself, in words
            // nobody reviewed — and a gateway delivers only registered templates anyway.
            return "XATO: bu agentda SMS shabloni yo'q, SMS yuborib bo'lmaydi. "
                    + "Mijozga hozir SMS yubora olmasligingizni ayting.";
        }
        if (SMS_TEXT_SLOT.matcher(template).find() && (text == null || text.isBlank())) {
            return "XATO: SMS shablonga qo'yiladigan havola yoki kodni text parametrida bering.";
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
        return "SMS mijozning telefoniga yuborildi. Mijozga SMS yuborilganini bildiring.";
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
            return "XATO: SMS matni bo'sh. Yuboriladigan matnni yozing.";
        }
        String body = text.trim();
        if (body.length() > MAX_SMS_CHARS) {
            return "XATO: SMS matni " + body.length() + " belgi, ko'pi bilan " + MAX_SMS_CHARS
                    + " bo'lishi mumkin. Qisqaroq matn bilan qayta chaqiring.";
        }
        if (session.countToolCall(SMS_BUDGET_KEY) > 1) {
            return "XATO: bu qo'ng'iroqda SMS allaqachon yuborilgan, ikkinchisi yuborilmaydi. "
                    + "Mijozga oldingi SMSni ko'rishini ayting.";
        }
        if (!session.triggerMidCallSms(body)) {
            log.warn("[{}] SMS not sent ({} chars)", session.channelId(), body.length());
            return "XATO: SMS yuborilmadi. Mijozga SMS yuborildi demang, hozir yubora olmasligingizni ayting.";
        }
        return null;
    }

    @Tool(description = "Telefonni ko'targan odam qo'ng'iroq qilingan shaxs emasligi aniqlanganda chaqiriladi")
    public String recordWrongPerson(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                    @ToolParam(description = "tafsilot") String detail) {
        recordReply(reply);
        session.end(Disposition.WRONG_NUMBER);
        log.info("[{}] wrong person: {}", session.channelId(), detail);
        return "Noto'g'ri odam belgilandi. Uzr so'rab xayrlashing.";
    }

    @Tool(description = "Mijoz boshqa qo'ng'iroq qilmaslikni, raqamini o'chirishni qat'iy talab qilganda chaqiriladi")
    public String recordDoNotCall(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                  @ToolParam(description = "talab sababi") String reason) {
        recordReply(reply);
        session.setDoNotCallReason(reason);
        session.end(Disposition.DO_NOT_CALL);
        log.info("[{}] do not call registered: reason={}", session.channelId(), reason);
        return "Raqam qora ro'yxatga kiritildi. Qisqa uzr so'rab xayrlashing.";
    }

    @Tool(description = "Mijoz to'lov havolasini yoki rekvizitlarni SMS orqali yuborishni so'raganda chaqiriladi")
    public String sendSmsPaymentLink(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                     @ToolParam(description = "SMS xabar turi yoki qo'shimcha matn", required = false) String note) {
        session.recordOutcome("sendSmsRequested", true);
        session.recordOutcome("smsNote", note);
        String link = findPaymentLink(session.context());
        if (link == null) {
            // No link on this call to send from here: the request stays recorded for whoever
            // follows up after the call, as it always was.
            recordReply(reply);
            log.info("[{}] SMS payment link requested, none on this call", session.channelId());
            return "To'lov havolasi SMS orqali yuboriladigan bo'ldi.";
        }
        // The model decides when, never what: the text is fixed and the link is the call's own.
        String refusal = sendGuardedSms(PAYMENT_LINK_SMS_TEXT + link);
        if (refusal != null) {
            return refusal;
        }
        recordReply(reply);
        session.recordOutcome("paymentLinkSmsSent", true);
        log.info("[{}] payment link SMS sent", session.channelId());
        return "To'lov havolasi mijozning telefoniga SMS orqali yuborildi. Mijozga SMSni tekshirishni ayting.";
    }

    /**
     * The call's payment link, when its {@value #PAYMENT_LINK_FACT} fact is a whole web
     * address. A fact longer than {@link CallContext} keeps arrives cut and ending in "…",
     * and half a link is worse than none.
     */
    private static String findPaymentLink(CallContext context) {
        return findLink(context, null, PAYMENT_LINK_FACT);
    }

    @Tool(description = "Mijoz to'lov muddatini bir necha kunga uzaytirishni (kechiktirishni) so'raganda chaqiriladi")
    public String requestPaymentExtension(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                          @ToolParam(description = "necha kunga uzaytirish so'ralmoqda") int extensionDays,
                                          @ToolParam(description = "kechiktirish sababi") String reason) {
        recordReply(reply);
        session.recordOutcome("extensionRequestedDays", extensionDays);
        session.recordOutcome("extensionReason", reason);
        log.info("[{}] payment extension requested: {} days, reason: {}", session.channelId(), extensionDays, reason);
        return "To'lov muddatini uzaytirish bo'yicha ariza qayd etildi: " + extensionDays + " kun.";
    }

    /**
     * "I already paid." Not argued with and not a refusal: the claim is filed, and
     * {@link Disposition#PAYMENT_CLAIMED} keeps the dialer off the number for three days
     * while the company checks it ({@code RetrySchedule}).
     */
    @Tool(description = "Mijoz qarzni ALLAQACHON to'laganini aytganda chaqiriladi. Avval qachon va qaysi "
            + "to'lov tizimi orqali to'laganini so'rang. Mijoz bilan tortishmang: to'lov tekshirilguncha "
            + "unga qo'ng'iroq qilinmasligini ayting")
    public String recordPaymentClaim(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                     @ToolParam(description = "mijoz aytgan to'lov sanasi, format yyyy-MM-dd",
                                             required = false) LocalDate paidDate,
                                     @ToolParam(description = "to'lov kanali: CLICK, PAYME, UZUM, BANK_BRANCH, "
                                             + "TERMINAL yoki OTHER", required = false) String paymentChannel) {
        if (paidDate != null && paidDate.isAfter(LocalDate.now())) {
            return "XATO: " + paidDate + " kelajakda. Bugun " + LocalDate.now()
                    + ". To'langan sana bugun yoki undan oldin bo'ladi — mijozdan aniqlang.";
        }
        recordReply(reply);
        session.recordOutcome("paidDate", paidDate);
        session.recordOutcome("paymentChannel", paymentChannel);
        session.setDisposition(Disposition.PAYMENT_CLAIMED);
        log.info("[{}] payment claimed: date={}, channel={}", session.channelId(), paidDate, paymentChannel);
        return "To'lov da'vosi yozib olindi. Tekshiruv tugaguncha 3 kun qo'ng'iroq qilinmaydi.";
    }

    /**
     * IVR navigation. Only reaches the model when the scenario declares a {@code ToolDef}
     * named {@code sendDtmfTones} — an outbound scenario that dials companies rather than
     * people (see {@code TurnTools#build}). The scenario's own {@code ToolDef.description}
     * is not used here: the wording below is what stops the model asking the human to press
     * the key, or guessing a digit it has not heard the menu name.
     */
    @Tool(description = "Narigi tomonda avtomat menyu (IVR) gapirganda kerakli tugmani BOT o'zi bosadi. "
            + "Menyuni oxirigacha yoki kerakli punkt aytilgunicha tinglang, keyin shu tool'ni faqat mos "
            + "raqam bilan chaqiring. Odamdan tugma bosishni SO'RAMANG. Qaysi raqam ekani aniq bo'lmasa "
            + "chaqirmang — tinglashda davom eting.")
    public String sendDtmfTones(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                                @ToolParam(description = "bosiladigan tugmalar: 0-9, * yoki #. Odatda bitta raqam") String digits,
                                @ToolParam(description = "menyuning shu raqamni qaysi so'z bilan atagani", required = false) String menuOption) {
        recordReply(reply);
        if (!session.sendDtmf(digits)) {
            log.warn("[{}] sendDtmfTones('{}') on a call with no keypad", session.channelId(), digits);
            return "XATO: bu qo'ng'iroqda tugma bosib bo'lmaydi. Menyuni kutmang, gapirib davom eting.";
        }
        log.info("[{}] IVR: pressed '{}' for '{}'", session.channelId(), digits, menuOption);
        return "'" + digits + "' bosildi. Endi menyu javobini kuting.";
    }

    @Tool(description = "Mijozning gapi hali tugamagan bo'lsa chaqiriladi: u o'ylanyapti, faqat "
            + "o'ylanish tovushi chiqardi yoki jumlasi chala qolib, davomi kelishi aniq. Bu holda "
            + "hech narsa demang va matn yozmang — bot jim turib, mijoz gapini davom ettirishini "
            + "kutadi, davomi esa shu gap bilan birga keladi. Mijoz savolni eshitmagan yoki "
            + "qaytarishni so'ragan bo'lsa, bu tool EMAS — oxirgi savolingizni qisqa qaytaring.")
    public String waitForCaller() {
        session.waitForCaller();
        log.info("[{}] model chose to keep listening", session.channelId());
        return "Jim turing, mijozning davomini kuting.";
    }

    @Tool(description = "Mijoz boshqa tilda gaplashishni so'raganda (qanday so'z bilan so'rasa ham) "
            + "chaqiriladi. Bu tool bilan birga oddiy matn YOZMANG — yangi tildagi gapingizni reply "
            + "parametriga yozing, u yangi tilning ovozi bilan aytiladi.")
    public String switchLanguage(@ToolParam(description = "mijozga yangi tilda aytiladigan gap") String reply,
                                 @ToolParam(description = "til kodi: uz-UZ, ru-RU yoki en-US") String language) {
        if (!session.switchCallLanguage(language)) {
            log.info("[{}] language switch to {} refused — not one of this company's languages",
                    session.channelId(), language);
            return "XATO: bu til ushbu qo'ng'iroqda qo'llab-quvvatlanmaydi. Mijozdan uzr so'rab, "
                    + "joriy tilda davom eting.";
        }
        // After the switch, so the line is synthesized in the new language's voice.
        recordReply(reply);
        log.info("[{}] caller asked for {} — language switched", session.channelId(), language);
        return "Til " + language + " ga o'tkazildi. Bundan keyin faqat shu tilda gapiring.";
    }

    @Tool(description = "Mijozning kayfiyati O'ZGARGANINI sezsangiz chaqiriladi: asabiylashdi "
            + "(FRUSTRATED), nima gapligini tushunmay qoldi (CONFUSED), mamnun (POSITIVE) yoki yana "
            + "xotirjam (NEUTRAL). Har navbatda emas — faqat o'zgarganda. Mijozga aytadigan gapingizni "
            + "odatdagidek oddiy matn qilib yozing; bu tool faqat ovoz ohangi va keyingi javob uslubini sozlaydi.")
    public String noteCallerMood(@ToolParam(description = "mijozning hozirgi kayfiyati") CustomerSentiment mood) {
        session.noteCallerMood(mood);
        log.info("[{}] caller mood noted: {}", session.channelId(), mood);
        return "Belgilandi.";
    }

    @Tool(description = "Suhbatni yakunlaydi va natijani belgilaydi")
    public String endCall(@ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
                          @ToolParam(description = "qo'ng'iroq yakunlash sababi/natijasi") Disposition disposition) {
        recordReply(reply);
        session.end(disposition);
        log.info("[{}] call ended with disposition: {}", session.channelId(), disposition);
        return "Qo'ng'iroq yakunlandi: " + disposition;
    }

    @Tool(description = "Mijoz ko'chmas mulk sotuv ofisiga (shourumga) kelishga rozi bo'lganda uchrashuvni band qiladi. "
            + "Uchrashuv vaqti, qiziqayotgan xonalar soni va to'lov turini yozib oladi. "
            + "Faqat mijoz tashrif buyurishga aniq rozi bo'lganda chaqiring.")
    public String bookShowroomVisit(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "uchrashuv sanasi va vaqti (masalan '2026-09-20 15:00' yoki 'ertaga soat 15:00')") String visitTime,
            @ToolParam(description = "kerakli xonalar soni (masalan '2 xonali', '3 xonali')", required = false) String roomsNeeded,
            @ToolParam(description = "taxminiy byudjet", required = false) String budgetRange,
            @ToolParam(description = "to'lov shakli (100% to'lov, bo'lib to'lash, ipoteka)", required = false) String paymentMethod,
            @ToolParam(description = "qo'shimcha izoh", required = false) String note) {
        if (visitTime == null || visitTime.isBlank()) {
            return "XATO: Uchrashuv vaqti ko'rsatilmadi. Mijozdan qaysi kun va soatda kela olishini so'rang.";
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
        return "Sotuv ofisiga uchrashuv belgilandi: " + visitTime.trim()
                + ". Mijozga mutaxassislarimiz kutib olishini bildiring va manzil/lokatsiyani SMS orqali yuborishni taklif qiling.";
    }

    @Tool(description = "Mijoz sotuv ofisi (shourum) manzili yoki lokatsiyasini so'raganda, "
            + "yoki uchrashuv belgilangandan so'ng SMS orqali lokatsiya va manzilni yuboradi.")
    public String sendLocationSms(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "lokatsiya havolasi (Google Maps yoki Yandex Maps)", required = false) String locationUrl,
            @ToolParam(description = "ofis manzili yoki mo'ljal", required = false) String address) {
        session.recordOutcome("locationSmsRequested", true);
        String link = findLink(session.context(), locationUrl, "locationLink", "showroomLocation", "mapUrl");
        String officeAddress = address != null && !address.isBlank()
                ? address.trim()
                : findLocationAddress(session.context());

        if (link == null && officeAddress == null) {
            recordReply(reply);
            log.info("[{}] location SMS requested but no location link/address found", session.channelId());
            return "Lokatsiya ma'lumotlari SMS orqali yuborish uchun qayd etildi.";
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
        return "Sotuv ofisi lokatsiyasi mijozning telefoniga SMS orqali yuborildi. Mijozga SMSni tekshirib olishini ayting.";
    }

    @Tool(description = "Mijoz turar joy majmuasi loyihalari, rejalari yoki taqdimotini (katalog) so'raganda "
            + "SMS orqali katalog havolasini yuboradi.")
    public String sendCatalogSms(
            @ToolParam(description = REPLY_DESCRIPTION, required = false) String reply,
            @ToolParam(description = "katalog yoki taqdimot havolasi", required = false) String catalogUrl) {
        session.recordOutcome("catalogSmsRequested", true);
        String link = findLink(session.context(), catalogUrl, "catalogUrl", "presentationUrl");
        if (link == null) {
            recordReply(reply);
            log.info("[{}] catalog SMS requested but no link found", session.channelId());
            return "Katalog havolasi SMS orqali yuborish uchun qayd etildi.";
        }
        String refusal = sendGuardedSms("Loyihalarimiz katalogi va rejalari: " + link);
        if (refusal != null) {
            return refusal;
        }
        recordReply(reply);
        session.recordOutcome("catalogSmsSent", true);
        log.info("[{}] catalog SMS sent successfully", session.channelId());
        return "Katalog havolasi mijozga SMS orqali yuborildi. Mijozga SMS kelganini ma'lum qiling.";
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
