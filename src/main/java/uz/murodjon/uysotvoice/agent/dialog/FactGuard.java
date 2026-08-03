package uz.murodjon.uysotvoice.agent.dialog;

import uz.murodjon.uysotvoice.scenario.dto.FactField;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>Known limit: this reads digits, so an amount the model spells out in words ("bir
 * million besh yuz ming") passes unchecked. Models write sums as digits when the prompt
 * gives them as digits, which is the case here, but the guard is a net under the prompt
 * rather than a replacement for it.
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

    private FactGuard() {
    }

    /**
     * Money-scale numbers in {@code text} that do not appear in {@code context}'s facts,
     * per {@code scenario}'s factSchema (ROADMAP A.3).
     *
     * @return the offending numbers as written, in order; empty when the text is safe
     */
    public static List<String> violations(String text, ScenarioDefinition scenario, CallContext context) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<BigDecimal> allowed = allowedValues(scenario, context);
        List<String> bad = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String raw = m.group();
            BigDecimal value = parse(raw);
            if (value == null || value.compareTo(MONEY_SCALE) < 0) {
                continue; // unparsable or conversational scale — not a money claim
            }
            if (value.compareTo(YEAR_MIN) >= 0 && value.compareTo(YEAR_MAX) <= 0
                    && value.stripTrailingZeros().scale() <= 0) {
                continue; // a year, not a sum
            }
            if (allowed.stream().noneMatch(a -> a.compareTo(value) == 0)) {
                bad.add(raw.trim());
            }
        }
        return bad;
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
