package uz.murodjon.uysotvoice.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Tools the LLM can call to drive the FSM and record outcomes (PROJECT.md §4.2). A
 * fresh instance is bound to one {@link DialogSession} per turn and passed to the
 * ChatClient. Return strings are fed back to the model as tool results. Code-level
 * guardrails (§4.4) live here — e.g. rejecting a past promised date.
 */
public class DialogTools {

    private static final Logger log = LoggerFactory.getLogger(DialogTools.class);

    /**
     * The two tools here that are scenario-specific rather than universal (ROADMAP
     * A.3) — only registered for a call when its scenario declares a {@link
     * uz.murodjon.uysotvoice.scenario.dto.ToolDef} with a matching name (see {@link
     * DialogEngine#toolsFor}). They stay hardcoded Java methods, unlike every other
     * scenario tool, because they carry real code-level guardrails (e.g. rejecting a
     * past promised date) that a purely declarative {@code ToolDef} cannot express.
     */
    static final Set<String> HARDCODED_TOOL_NAMES = Set.of("recordPaymentPromise", "recordRefusalReason");

    private final DialogSession session;

    public DialogTools(DialogSession session) {
        this.session = session;
    }

    @Tool(description = "Suhbat bosqichini keyingi ruxsat etilgan holatga o'tkazadi")
    public String transitionTo(@ToolParam(description = "keyingi bosqich id'si") String nextStage) {
        session.setState(nextStage);
        log.info("[{}] dialog state -> {}", session.channelId(), nextStage);
        return "Holat " + nextStage + " ga o'tkazildi";
    }

    @Tool(description = "Mijoz aniq to'lov sanasini va'da qilganda chaqiriladi")
    public String recordPaymentPromise(
            @ToolParam(description = "va'da qilingan sana, format yyyy-MM-dd") LocalDate promisedDate,
            @ToolParam(description = "va'da qilingan summa", required = false) BigDecimal amount,
            @ToolParam(description = "qo'shimcha izoh", required = false) String note) {
        // Guardrail: never accept a promise in the past (§4.4).
        if (promisedDate != null && promisedDate.isBefore(LocalDate.now())) {
            // Name today's date: the usual cause is a wrong year, and without it the
            // model has nothing to correct against and re-sends the same date.
            return "XATO: " + promisedDate + " o'tmishda. Bugun " + LocalDate.now()
                    + ". Sanani shundan hisoblab qaytadan yuboring yoki mijozdan aniq sanani so'rang.";
        }
        session.recordOutcome("promisedDate", promisedDate);
        session.recordOutcome("promisedAmount", amount);
        session.setDisposition(Disposition.PROMISE_TO_PAY);
        log.info("[{}] payment promise: date={}, amount={}, note={}",
                session.channelId(), promisedDate, amount, note);
        return "To'lov va'dasi yozib olindi: " + promisedDate;
    }

    @Tool(description = "Mijoz to'lay olmasligini aytganda sababni yozib qo'yadi")
    public String recordRefusalReason(
            @ToolParam(description = "to'lamaslik sababi") String reason,
            @ToolParam(description = "sabab tafsiloti", required = false) String detail) {
        session.recordOutcome("reasonCode", reason);
        session.setDisposition(Disposition.REFUSED);
        log.info("[{}] refusal reason: {} ({})", session.channelId(), reason, detail);
        return "Sabab yozib olindi: " + reason;
    }

    @Tool(description = "Mijoz operator bilan gaplashishni so'raganda yoki janjal qilganda operatorga o'tkazadi")
    public String requestHumanTransfer(@ToolParam(description = "o'tkazish sababi") String reason) {
        session.end(Disposition.TRANSFERRED);
        log.info("[{}] human transfer requested: {}", session.channelId(), reason);
        return "Operatorga o'tkazish so'raldi. Mijoz bilan xayrlashing.";
    }

    @Tool(description = "Telefonni ko'targan odam qarzdor emasligi aniqlanganda chaqiriladi")
    public String recordWrongPerson(@ToolParam(description = "tafsilot") String detail) {
        session.end(Disposition.WRONG_NUMBER);
        log.info("[{}] wrong person: {}", session.channelId(), detail);
        return "Noto'g'ri odam belgilandi. Uzr so'rab xayrlashing.";
    }

    @Tool(description = "Mijoz boshqa qo'ng'iroq qilinmasligini so'raganda chaqiriladi")
    public String recordDoNotCall(@ToolParam(description = "mijozning so'rovi/sababi") String reason) {
        // §11.4: the opt-out is a legal obligation, so it is recorded against the
        // phone number at teardown, not just against this campaign's target row.
        session.setDoNotCallReason(reason);
        session.end(Disposition.DO_NOT_CALL);
        log.info("[{}] do-not-call requested: {}", session.channelId(), reason);
        return "So'rov qabul qilindi, raqam ro'yxatdan chiqariladi. Uzr so'rab xayrlashing.";
    }

    @Tool(description = "Suhbat tugadi — qo'ng'iroqni yakunlaydi (avval xayrlashing)")
    public String endCall(@ToolParam(description = "qo'ng'iroq natijasi. Qarz undirishga oid "
            + "bo'lmagan ssenariylarda (so'rovnoma, xabar va h.k.) odatda COMPLETED ishlatiladi")
            Disposition disposition) {
        session.end(disposition);
        log.info("[{}] end call requested: disposition={}", session.channelId(), disposition);
        return "Qo'ng'iroq yakunlanadi. Mijoz bilan qisqa xayrlashing.";
    }
}
