package uz.murodjon.robotcallv2.agent.dialog;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a {@code yyyy-MM-dd} date into the words a person would say.
 *
 * <p>The machine form belongs in a tool parameter and nowhere else — the prompt says so —
 * but the model writes one into its reply anyway, and a synthesizer handed "2026-07-01"
 * reads the caller a run of digits instead of a due date. Rewriting it here rather than
 * asking the prompt again is the difference between a rule and a guarantee: this is the
 * one place every spoken sentence passes through.
 *
 * <p>The year is dropped when it is the current one, for the same reason a person drops
 * it: "1-iyul" is what the caller is waiting to hear, and "2026-yil 1-iyul" sounds like a
 * form being read out.
 */
final class SpokenDates {

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

    private static final String[] UZ_MONTHS = {
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"};

    private static final String[] RU_MONTHS = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private static final String[] EN_MONTHS = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    private SpokenDates() {
    }

    /** {@code text} with every ISO date replaced by its spoken form in {@code language}. */
    static String humanize(String text, String language) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = ISO_DATE.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int currentYear = LocalDate.now().getYear();
        do {
            LocalDate date = parse(matcher.group(1), matcher.group(2), matcher.group(3));
            // Anything that is not a real date is left exactly as it was: a contract
            // number shaped like one is still the caller's contract number.
            String replacement = date == null ? matcher.group() : spell(date, currentYear, language);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        } while (matcher.find());
        matcher.appendTail(out);
        return out.toString();
    }

    private static LocalDate parse(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static String spell(LocalDate date, int currentYear, String language) {
        int index = date.getMonthValue() - 1;
        boolean thisYear = date.getYear() == currentYear;
        if (language != null && language.startsWith("ru")) {
            return date.getDayOfMonth() + " " + RU_MONTHS[index]
                    + (thisYear ? "" : " " + date.getYear() + " года");
        }
        if (language != null && language.startsWith("en")) {
            return EN_MONTHS[index] + " " + date.getDayOfMonth()
                    + (thisYear ? "" : ", " + date.getYear());
        }
        return (thisYear ? "" : date.getYear() + "-yil ")
                + date.getDayOfMonth() + "-" + UZ_MONTHS[index];
    }
}
