package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Set;

/**
 * The barge-in decisions that do not need the model ({@code LlmTurnClient#isTakingTheFloor}):
 * the bot is paused while that call runs, for up to a second and a half, on every
 * word or two said over it.
 *
 * <p>Two cases are certain from the words alone. A "no", a "wait" or a question word is
 * the caller taking the floor whatever line it was said over. Nothing but listener sounds
 * ("aha", "xo'p", "угу") over a line that asked nothing is the caller letting the bot go
 * on. Everything else — above all a "ha" over a question, which is an answer — still goes
 * to the model: the line it was said over decides it, and that is the model's to read.
 */
final class FloorRules {

    /** As {@link Backchannels#words} leaves them: lower case, apostrophes dropped. */
    private static final Set<String> TAKING_THE_FLOOR = Set.of(
            "yoq", "kerakmas", "kerak", "emas", "toxtang", "toxta", "shoshmang", "kuting",
            "nima", "nega", "qanaqa", "qanday", "qachon", "qayer", "qayerda", "kim", "qancha", "nimaga",
            "нет", "не", "стоп", "подождите", "погодите", "стойте", "что", "почему", "зачем", "как",
            "когда", "где", "кто", "сколько",
            "no", "stop", "wait", "what", "why");

    private static final Set<String> LISTENING = Set.of(
            "ha", "xa", "haa", "aha", "ahha", "mhm", "mm", "mmm", "hm", "xop", "xopp", "mayli",
            "yaxshi", "tushunarli", "tushundim", "eshitaman", "ok", "okey",
            "да", "ага", "угу", "ну", "так", "ясно", "понятно", "хорошо", "конечно", "слушаю",
            "yes", "yeah", "uh", "huh");

    private FloorRules() {
    }

    /**
     * @param botLine    what the bot was saying when it was talked over
     * @param callerText the caller's words
     * @return true when the caller is taking the floor, false when they let the bot carry
     *         on, null when only the model can tell
     */
    static Boolean decide(String botLine, String callerText) {
        if (callerText == null || callerText.isBlank()) {
            return null;
        }
        if (callerText.contains("?")) {
            return true;
        }
        String[] words = Backchannels.words(callerText);
        boolean listening = true;
        for (String word : words) {
            if (TAKING_THE_FLOOR.contains(word)) {
                return true;
            }
            listening &= LISTENING.contains(word);
        }
        boolean botAsked = botLine != null && botLine.contains("?");
        return listening && !botAsked ? false : null;
    }
}
