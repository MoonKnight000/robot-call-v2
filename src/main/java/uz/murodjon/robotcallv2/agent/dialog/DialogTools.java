package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Tools the LLM can call to drive the FSM and record outcomes (PROJECT.md §4.2, MASTER_ROADMAP.md §5).
 */
public class DialogTools {

    private static final Logger log = LoggerFactory.getLogger(DialogTools.class);

    static final String REPLY_PARAM = "reply";
    static final String REPLY_DESCRIPTION =
            "shu tool bilan birga mijozga ovoz bilan aytiladigan gap — bo'sh qoldirmang";

    static final Set<String> HARDCODED_TOOL_NAMES = Set.of(
            "recordPaymentPromise", "recordRefusalReason", "scheduleCallback",
            "requestHumanTransfer", "recordWrongPerson", "recordDoNotCall",
            "sendSmsPaymentLink", "requestPaymentExtension", "endCall"
    );

    private final DialogOutcomeSink session;

    public DialogTools(DialogOutcomeSink session) {
        this.session = session;
    }

    @Tool(description = "Suhbat bosqichini keyingi ruxsat etilgan holatga o'tkazadi")
    public String transitionTo(@ToolParam(description = REPLY_DESCRIPTION) String reply,
                               @ToolParam(description = "keyingi bosqich id'si") String nextStage) {
        session.addToolReply(reply);
        session.setState(nextStage);
        log.info("[{}] dialog state -> {}", session.channelId(), nextStage);
        return "Holat " + nextStage + " ga o'tkazildi";
    }

    @Tool(description = "Mijoz aniq to'lov sanasini va'da qilganda chaqiriladi")
    public String recordPaymentPromise(
            @ToolParam(description = REPLY_DESCRIPTION) String reply,
            @ToolParam(description = "va'da qilingan sana, format yyyy-MM-dd") LocalDate promisedDate,
            @ToolParam(description = "va'da qilingan summa", required = false) BigDecimal amount,
            @ToolParam(description = "qo'shimcha izoh", required = false) String note) {
        if (promisedDate != null && promisedDate.isBefore(LocalDate.now())) {
            return "XATO: " + promisedDate + " o'tmishda. Bugun " + LocalDate.now()
                    + ". Sanani shundan hisoblab qaytadan yuboring yoki mijozdan aniq sanani so'rang.";
        }
        session.addToolReply(reply);
        session.recordOutcome("promisedDate", promisedDate);
        session.recordOutcome("promisedAmount", amount);
        session.setDisposition(Disposition.PROMISE_TO_PAY);
        log.info("[{}] payment promise: date={}, amount={}, note={}",
                session.channelId(), promisedDate, amount, note);
        return "To'lov va'dasi yozib olindi: " + promisedDate;
    }

    @Tool(description = "Mijoz to'lay olmasligini aytganda sababni yozib qo'yadi")
    public String recordRefusalReason(
            @ToolParam(description = REPLY_DESCRIPTION) String reply,
            @ToolParam(description = "to'lamaslik sababi") String reason,
            @ToolParam(description = "sabab tafsiloti", required = false) String detail) {
        session.addToolReply(reply);
        session.recordOutcome("reasonCode", reason);
        session.setDisposition(Disposition.REFUSED);
        log.info("[{}] refusal reason: {} ({})", session.channelId(), reason, detail);
        return "Sabab yozib olindi: " + reason;
    }

    @Tool(description = "Mijoz keyinroq (masalan ertaga yoki soat 16:00 da) qayta qo'ng'iroq qilishni so'raganda chaqiriladi")
    public String scheduleCallback(
            @ToolParam(description = REPLY_DESCRIPTION) String reply,
            @ToolParam(description = "qayta qo'ng'iroq sanasi va vaqti (masalan 2026-09-02T16:00:00 yoki ertaga soat 16:00)") String callbackTime,
            @ToolParam(description = "sababi yoki izoh", required = false) String reason) {
        session.addToolReply(reply);
        session.recordOutcome("callbackTime", callbackTime);
        session.recordOutcome("callbackReason", reason);
        session.setDisposition(Disposition.CALLBACK_REQUESTED);
        log.info("[{}] callback requested: time={}, reason={}", session.channelId(), callbackTime, reason);
        return "Qayta qo'ng'iroq rejalashtirildi: " + callbackTime;
    }

    @Tool(description = "Mijoz operator bilan gaplashishni so'raganda yoki janjal qilganda operatorga o'tkazadi")
    public String requestHumanTransfer(@ToolParam(description = REPLY_DESCRIPTION) String reply,
                                       @ToolParam(description = "o'tkazish sababi") String reason) {
        session.addToolReply(reply);
        session.end(Disposition.TRANSFERRED);
        log.info("[{}] human transfer requested: {}", session.channelId(), reason);
        return "Operatorga o'tkazish so'raldi. Mijoz bilan xayrlashing.";
    }

    @Tool(description = "Telefonni ko'targan odam qarzdor emasligi aniqlanganda chaqiriladi")
    public String recordWrongPerson(@ToolParam(description = REPLY_DESCRIPTION) String reply,
                                    @ToolParam(description = "tafsilot") String detail) {
        session.addToolReply(reply);
        session.end(Disposition.WRONG_NUMBER);
        log.info("[{}] wrong person: {}", session.channelId(), detail);
        return "Noto'g'ri odam belgilandi. Uzr so'rab xayrlashing.";
    }

    @Tool(description = "Mijoz boshqa qo'ng'iroq qilmaslikni, raqamini o'chirishni qat'iy talab qilganda chaqiriladi")
    public String recordDoNotCall(@ToolParam(description = REPLY_DESCRIPTION) String reply,
                                  @ToolParam(description = "talab sababi") String reason) {
        session.addToolReply(reply);
        session.setDoNotCallReason(reason);
        session.end(Disposition.DO_NOT_CALL);
        log.info("[{}] do not call registered: reason={}", session.channelId(), reason);
        return "Raqam qora ro'yxatga kiritildi. Qisqa uzr so'rab xayrlashing.";
    }

    @Tool(description = "Mijoz to'lov havolasini yoki rekvizitlarni SMS orqali yuborishni so'raganda chaqiriladi")
    public String sendSmsPaymentLink(@ToolParam(description = REPLY_DESCRIPTION) String reply,
                                     @ToolParam(description = "SMS xabar turi yoki qo'shimcha matn", required = false) String note) {
        session.addToolReply(reply);
        session.recordOutcome("sendSmsRequested", true);
        session.recordOutcome("smsNote", note);
        log.info("[{}] SMS payment link requested: note={}", session.channelId(), note);
        return "To'lov havolasi SMS orqali yuboriladigan bo'ldi.";
    }

    @Tool(description = "Mijoz to'lov muddatini bir necha kunga uzaytirishni (kechiktirishni) so'raganda chaqiriladi")
    public String requestPaymentExtension(@ToolParam(description = REPLY_DESCRIPTION) String reply,
                                          @ToolParam(description = "necha kunga uzaytirish so'ralmoqda") int extensionDays,
                                          @ToolParam(description = "kechiktirish sababi") String reason) {
        session.addToolReply(reply);
        session.recordOutcome("extensionRequestedDays", extensionDays);
        session.recordOutcome("extensionReason", reason);
        log.info("[{}] payment extension requested: {} days, reason: {}", session.channelId(), extensionDays, reason);
        return "To'lov muddatini uzaytirish bo'yicha ariza qayd etildi: " + extensionDays + " kun.";
    }

    @Tool(description = "Suhbatni yakunlaydi va natijani belgilaydi")
    public String endCall(@ToolParam(description = REPLY_DESCRIPTION) String reply,
                          @ToolParam(description = "qo'ng'iroq yakunlash sababi/natijasi") Disposition disposition) {
        session.addToolReply(reply);
        session.end(disposition);
        log.info("[{}] call ended with disposition: {}", session.channelId(), disposition);
        return "Qo'ng'iroq yakunlandi: " + disposition;
    }
}
