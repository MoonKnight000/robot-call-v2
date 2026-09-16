package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.Locale;

/**
 * One ✗ → ✓ pair of phrasing a scenario teaches its agent by — the domain's own examples,
 * so the platform prompt stops carrying one domain's sentences into every scenario's calls
 * (UNIVERSAL-OPERATOR-PLAN A.2).
 *
 * @param language the call language the pair is written in, by prefix ({@code "uz"},
 *                 {@code "ru"}); null for every language
 * @param wrong    what the agent should not say — best a line a real call produced
 * @param right    what it should have said instead
 */
public record StyleExample(String language, String wrong, String right) {

    /** Whether this pair belongs in the prompt of a call held in {@code callLanguage}. */
    public boolean appliesTo(String callLanguage) {
        if (language == null || language.isBlank()) {
            return true;
        }
        return callLanguage != null
                && callLanguage.toLowerCase(Locale.ROOT).startsWith(language.trim().toLowerCase(Locale.ROOT));
    }
}
