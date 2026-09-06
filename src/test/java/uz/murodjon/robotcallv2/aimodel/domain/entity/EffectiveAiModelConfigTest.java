package uz.murodjon.robotcallv2.aimodel.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A scenario may tune the model it speaks with, but it may not raise the limits the company
 * put on spending — those two rules are the whole of this class.
 */
class EffectiveAiModelConfigTest {

    private static final EffectiveAiModelConfig COMPANY =
            new EffectiveAiModelConfig("gemini-2.5-pro", 0.4, 500, 300, 40_000L);

    @Test
    void nothingOverriddenReturnsTheSameInstance() {
        assertThat(COMPANY.withOverrides(null, null, null)).isSameAs(COMPANY);
        assertThat(COMPANY.withOverrides("  ", null, null)).isSameAs(COMPANY);
    }

    @Test
    void aScenarioMayPickItsOwnModel() {
        EffectiveAiModelConfig merged = COMPANY.withOverrides("gemini-2.5-flash", null, null);

        assertThat(merged.model()).isEqualTo("gemini-2.5-flash");
        assertThat(merged.temperature()).isEqualTo(0.4);
        assertThat(merged.maxOutputTokens()).isEqualTo(500);
    }

    @Test
    void eachFieldFallsBackIndependently() {
        EffectiveAiModelConfig merged = COMPANY.withOverrides(null, 0.9, null);

        assertThat(merged.model()).isEqualTo("gemini-2.5-pro");
        assertThat(merged.temperature()).isEqualTo(0.9);
        assertThat(merged.maxOutputTokens()).isEqualTo(500);
    }

    @Test
    void temperatureZeroIsAValueAndNotAnAbsentOne() {
        assertThat(COMPANY.withOverrides(null, 0.0, null).temperature()).isEqualTo(0.0);
    }

    @Test
    void spendLimitsStayWithTheCompany() {
        EffectiveAiModelConfig merged = COMPANY.withOverrides("gemini-2.5-flash", 1.5, 900);

        assertThat(merged.maxCallSeconds()).isEqualTo(300);
        assertThat(merged.maxTokensPerCall()).isEqualTo(40_000L);
    }
}
