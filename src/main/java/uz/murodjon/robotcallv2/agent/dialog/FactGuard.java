package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code-level check that the agent only speaks the money figures it was given
 * (PROJECT.md §4.4: "LLM javobida raqam bo'lsa, u kontekstdagi faktlar bilan
 * solishtiriladi").
 *
 * <p>The system prompt already forbids altering the amount, and a prompt is not a
 * control. In debt collection a hallucinated figure is the worst thing this system can
 * do: the caller hears an official demand for money they do not owe, and the recording
 * proves the company said it. So the amount is verified before it reaches the wire, not
 * after.
 *
 * <p>Only <b>money-scale</b> numbers are checked. A telephone conversation is full of
 * small ones — "3 kun", "5-sana", "2 hafta" — and treating those as facts would block
 * ordinary speech while catching nothing that matters. Everything at or above
 * {@link #MONEY_SCALE}, outside the year window, has to match a figure from the call's
 * facts (ROADMAP A.3): a {@code number}-type fact (in any of the ways it can be
 * written), a {@code date}-type fact's year, or a run of digits embedded in a
 * {@code string}-type fact (e.g. a contract number).
 *
 * <p>The facts are not the only place a figure may come from. A company tool's answer is
 * the other ({@link DialogOutcomeSink#recordToolFigures}): a shop's catalog says the price,
 * its order endpoint the order number, and an agent reading them back states what it was
 * given (UNIVERSAL-OPERATOR-PLAN A.3). The rule stays the same — every figure said has to
 * have come from somewhere — only the somewhere is wider. What the guard cannot tell apart
 * is an endpoint that echoes back a figure the model sent it; counting only a successful
 * answer narrows that, and the rest is on whoever configured the tool.
 *
 * <p>Figures spelled out in words are checked too ({@link UzbekNumberParser},
 * {@link RussianNumberParser}). That used to be a documented gap, on the reasoning that a
 * prompt which gives sums as digits gets them back as digits. It holds for the cascade
 * pipeline and not for a realtime one: there the guard reads a transcript of speech, so
 * the form is whatever the engine happened to say, and the "write sums as digits" rule
 * the cascade prompt carries exists to serve a TTS step that a realtime call does not have.
 *
 * <p>Dates and day counts are deliberately <b>not</b> checked. A payment date is usually
 * one the caller proposed, so it cannot be among the facts, and the bot has to read it
 * back to confirm it; "3 kun ichida" or "15-sana" are conversational numbers below
 * {@link #MONEY_SCALE}. Checking them would block ordinary confirmations while catching
 * nothing the §4.4 rule is about — a wrong sum or an invented discount.
 */
public final class FactGuard {

    /**
     * Smallest value treated as a money claim. Below this a number is conversational
     * (days, dates, counts) — and no real debt in so'm is three digits.
     */
    private static final BigDecimal MONEY_SCALE = new BigDecimal("1000");

    /**
     * Years are money-scale numbers that are never money. The bot confirms dates out
     * loud constantly ("2027-yil 15-avgust"), and a promised year the client proposed
     * cannot be in the context facts, so without this window every date confirmation
     * would be blocked as a hallucinated sum.
     */
    private static final BigDecimal YEAR_MIN = new BigDecimal("1900");
    private static final BigDecimal YEAR_MAX = new BigDecimal("2100");

    /**
     * Digit groups, allowing the separators a model uses when writing a sum:
     * {@code 1500000}, {@code 1 500 000}, {@code 1.500.000}, {@code 1,500,000}.
     * The decimal tail is captured so "1500000.00" is compared as a number, not text.
     */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d\\s.,\\u00A0']*\\d|\\d");

    /**
     * A percentage, in digits: "20%", "20 %", "20 foiz", "20 процентов". Checked at any
     * scale, unlike other numbers — a discount the company never offered is exactly the
     * claim PROJECT.md §4.4 forbids, and "20" alone is too small to be caught as money.
     * Day counts and dates stay unchecked: "3 kun ichida" is ordinary speech, and a date
     * the caller proposed cannot be in the facts.
     */
    private static final Pattern PERCENT = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(?:%|foiz|protsent|процент)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private FactGuard() {
    }

    /**
     * Money-scale numbers in {@code text} that are neither in {@code context}'s facts, per
     * {@code scenario}'s factSchema (ROADMAP A.3), nor among the figures this call's company
     * tools answered with.
     *
     * @param toolFigures what company tools answered with on this call; empty when none did
     * @return the offending numbers as written, in order; empty when the text is safe
     */
    public static List<String> violations(String text, ScenarioDefinition scenario, CallContext context,
                                          Collection<BigDecimal> toolFigures) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<BigDecimal> allowed = allowedValues(scenario, context);
        if (toolFigures != null) {
            allowed.addAll(toolFigures);
        }
        List<String> bad = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String raw = m.group();
            BigDecimal value = parse(raw);
            if (value != null && isUnexplainedClaim(value, allowed)) {
                bad.add(raw.trim());
            }
        }
        // ...and the same figures spelled out. Both paths end in the same three filters,
        // because "besh million" and "5000000" are the same claim to the person hearing it.
        for (SpelledNumber spelled : spelledNumbers(text)) {
            if (isUnexplainedClaim(spelled.value(), allowed)) {
                bad.add(spelled.words());
            }
        }
        Matcher percent = PERCENT.matcher(text);
        while (percent.find()) {
            BigDecimal value = new BigDecimal(percent.group(1).replace(',', '.'));
            if (allowed.stream().noneMatch(a -> a.compareTo(value) == 0)) {
                bad.add(percent.group().trim());
            }
        }
        return bad;
    }

    /**
     * Every number {@code text} states, however it is written — what a tool's text answer
     * puts in front of the model, and so what it may then say
     * ({@link DialogOutcomeSink#recordToolFigures}).
     */
    static List<BigDecimal> figuresIn(String text) {
        List<BigDecimal> figures = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return figures;
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            BigDecimal value = parse(m.group());
            if (value != null) {
                figures.add(value);
            }
        }
        for (SpelledNumber spelled : spelledNumbers(text)) {
            figures.add(spelled.value());
        }
        return figures;
    }

    /** Figures spelled out in words, in either language a call is held in. */
    private static List<SpelledNumber> spelledNumbers(String text) {
        List<SpelledNumber> spelled = new ArrayList<>(UzbekNumberParser.findAll(text));
        spelled.addAll(RussianNumberParser.findAll(text));
        return spelled;
    }

    /** Money-scale, not a year, and not one of the figures this call was given. */
    private static boolean isUnexplainedClaim(BigDecimal value, Set<BigDecimal> allowed) {
        if (value.compareTo(MONEY_SCALE) < 0) {
            return false; // conversational scale — not a money claim
        }
        if (value.compareTo(YEAR_MIN) >= 0 && value.compareTo(YEAR_MAX) <= 0
                && value.stripTrailingZeros().scale() <= 0) {
            return false; // a year, not a sum
        }
        return allowed.stream().noneMatch(a -> a.compareTo(value) == 0);
    }

    /** Every money-scale figure the agent is allowed to say out loud, per the scenario's factSchema. */
    private static Set<BigDecimal> allowedValues(ScenarioDefinition scenario, CallContext context) {
        Set<BigDecimal> allowed = new LinkedHashSet<>();
        if (scenario == null || scenario.factSchema() == null || context == null) {
            return allowed;
        }
        Map<String, Object> facts = context.facts();
        for (FactField f : scenario.factSchema()) {
            Object value = facts.get(f.name());
            if (value == null) {
                continue;
            }
            switch (f.type()) {
                case "number" -> {
                    BigDecimal amount = value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
                    allowed.add(amount);
                    // A model that says "1 500 000 so'm 40 tiyin" or rounds the trailing
                    // zeros off a whole sum is still stating the same fact; only a
                    // different figure is a violation.
                    allowed.add(amount.stripTrailingZeros());
                }
                case "date" -> {
                    // A date read out as a bare year ("2026") is also given, not invented.
                    LocalDate date = value instanceof LocalDate d ? d : LocalDate.parse(value.toString());
                    allowed.add(BigDecimal.valueOf(date.getYear()));
                }
                default -> // A string fact (e.g. a contract number) may itself be full of
                        // long digit runs ("UY-2026-00123" -> 2026, 00123). Reading it
                        // back must not trip the guard.
                        allowed.addAll(digitRuns(value.toString()));
            }
        }
        return allowed;
    }

    private static List<BigDecimal> digitRuns(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<BigDecimal> runs = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+").matcher(value);
        while (m.find()) {
            runs.add(new BigDecimal(m.group()));
        }
        return runs;
    }

    /**
     * Read a written number, dropping the grouping separators. Ambiguous by nature —
     * "1.500" is fifteen hundred in Uzbek usage and one-and-a-half in English — so a
     * separator followed by exactly three digits is read as grouping, and anything else
     * as a decimal point. Guessing wrong only changes which value is compared, and a
     * genuine mismatch is still a mismatch either way.
     */
    private static BigDecimal parse(String raw) {
        String cleaned = raw.replaceAll("[\\s\\u00A0']", "");
        cleaned = cleaned.replaceAll("([.,])(\\d{3})(?=\\D|$)", "$2");
        cleaned = cleaned.replace(',', '.');
        // Any grouping separators left over (e.g. "1.500.000" → after the pass above)
        // are removed so the remainder parses.
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot >= 0) {
            cleaned = cleaned.substring(0, lastDot).replace(".", "") + cleaned.substring(lastDot);
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
