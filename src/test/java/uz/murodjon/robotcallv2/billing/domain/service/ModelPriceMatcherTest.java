package uz.murodjon.robotcallv2.billing.domain.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModelPriceMatcherTest {

    private static final Set<String> KNOWN = Set.of("gpt-4o", "gpt-4o-mini", "gemini-2.5-flash", "gemini-2.5-flash-lite");

    @Test
    void matchesTheExactId() {
        assertThat(ModelPriceMatcher.findMatch("gemini-2.5-flash", KNOWN)).isEqualTo("gemini-2.5-flash");
    }

    @Test
    void ignoresCaseAndGeminisModelsPrefix() {
        assertThat(ModelPriceMatcher.findMatch("models/Gemini-2.5-Flash", KNOWN)).isEqualTo("gemini-2.5-flash");
    }

    /** OpenAI answers with the dated snapshot of the model that was asked for. */
    @Test
    void matchesADatedSnapshotToItsModel() {
        assertThat(ModelPriceMatcher.findMatch("gpt-4o-mini-2024-07-18", KNOWN)).isEqualTo("gpt-4o-mini");
        assertThat(ModelPriceMatcher.findMatch("gemini-2.5-flash-preview-05-20", KNOWN)).isEqualTo("gemini-2.5-flash");
    }

    /** A different, cheaper model must never be priced as the one whose name it starts with. */
    @Test
    void doesNotPriceAnotherModelAsItsPrefix() {
        Set<String> onlyBig = Set.of("gpt-4o", "gemini-2.5-flash");

        assertThat(ModelPriceMatcher.findMatch("gpt-4o-mini", onlyBig)).isNull();
        assertThat(ModelPriceMatcher.findMatch("gemini-2.5-flash-lite", onlyBig)).isNull();
    }

    @Test
    void unknownOrBlankHasNoMatch() {
        assertThat(ModelPriceMatcher.findMatch("claude-x", KNOWN)).isNull();
        assertThat(ModelPriceMatcher.findMatch(" ", KNOWN)).isNull();
        assertThat(ModelPriceMatcher.findMatch(null, KNOWN)).isNull();
    }

    @Test
    void validatesStoredIds() {
        assertThat(ModelPriceMatcher.isValidId("gpt-4o-mini")).isTrue();
        assertThat(ModelPriceMatcher.isValidId("Gpt 4o")).isFalse();
        assertThat(ModelPriceMatcher.isValidId("")).isFalse();
    }
}
