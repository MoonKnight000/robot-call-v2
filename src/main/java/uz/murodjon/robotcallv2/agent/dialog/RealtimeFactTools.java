package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;

import java.util.ArrayList;
import java.util.List;

/**
 * The only way a realtime call can learn a figure: one tool, one fact, fetched at the
 * moment it is about to be said (§4.4).
 *
 * <p>Registered only for {@code PipelineMode.REALTIME} calls, and only when the values
 * are being kept out of the prompt ({@code voice-agent.realtime.facts-in-prompt=false}).
 * The cascade pipeline has no use for it: it is given the facts up front and checks the
 * sentence against them before anything is spoken.
 *
 * <p>Why a realtime call is different. Its engine speaks straight from audio, so the
 * guard has nothing to inspect until the caller has already heard the words
 * ({@code RealtimeDialogEngine.auditFacts}) — detection, not prevention. Prevention has
 * to come from somewhere else, and the only reliable place left is the input: an engine
 * that was never told the debt amount cannot misremember it. It can still fabricate one,
 * which is what the audit is for; the two layers are meant to be read together.
 *
 * <p>The cost is a tool round trip in the middle of a live turn, heard as a short pause
 * before a figure. That is the trade being made deliberately: a beat of silence against
 * an invented demand for money on a recorded line.
 */
public class RealtimeFactTools {

    private static final Logger log = LoggerFactory.getLogger(RealtimeFactTools.class);

    /** What the model gets for a fact this call does not carry — never a guess, never blank. */
    private static final String MISSING = "MA'LUMOT YO'Q — bu ma'lumotni aytmang, mijozdan so'rang "
            + "yoki operatorga o'tkazing.";

    private final RealtimeDialogSession session;

    public RealtimeFactTools(RealtimeDialogSession session) {
        this.session = session;
    }

    @Tool(description = "Mijoz haqidagi aniq ma'lumotni (summa, sana, shartnoma raqami, ism) qaytaradi. "
            + "Har qanday raqam yoki sanani aytishdan OLDIN shu tool'ni chaqiring va qaytgan qiymatni "
            + "aynan o'zgartirmasdan ayting. Xotirangizdan raqam aytish QAT'IYAN taqiqlanadi.")
    public String getCallFact(@ToolParam(description = "ma'lumot nomi, masalan debtAmount, dueDate, "
            + "contractNumber, clientName") String name) {
        if (name == null || name.isBlank()) {
            return MISSING;
        }
        Object value = session.context() != null ? session.context().fact(name.trim()) : null;
        if (value == null || value.toString().isBlank()) {
            log.info("[{}] fact '{}' requested but this call does not carry it", session.channelId(), name);
            return MISSING;
        }
        log.debug("[{}] fact '{}' served to the engine", session.channelId(), name);
        return value.toString();
    }

    /**
     * The fact names this call actually carries — what the prompt lists so the engine
     * knows what it may ask for, without any of the values appearing in it.
     */
    static List<String> availableFactNames(RealtimeDialogSession session) {
        List<FactField> schema = session.scenario().factSchema();
        if (schema == null || schema.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (FactField field : schema) {
            Object value = session.context() != null ? session.context().fact(field.name()) : null;
            if (value != null && !value.toString().isBlank()) {
                names.add(field.name());
            }
        }
        return names;
    }
}
