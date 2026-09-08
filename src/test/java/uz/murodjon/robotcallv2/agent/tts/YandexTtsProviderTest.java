package uz.murodjon.robotcallv2.agent.tts;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Yandex v3 rejects a synthesis request over {@link YandexTtsProvider#MAX_TEXT_CHARS}
 * outright ("Too long text"), and on a live call that cost the caller the whole line —
 * the number normalizer can push a single tool-carried reply over the limit. The split
 * has to keep every word and never produce a piece Yandex would refuse.
 */
class YandexTtsProviderTest {

    @Test
    void shortTextComesBackAsTheSinglePieceItWas() {
        assertThat(YandexTtsProvider.splitForSynthesis("Assalomu alaykum."))
                .containsExactly("Assalomu alaykum.");
    }

    @Test
    void longTextIsSplitAtSentenceEndsWithinTheLimit() {
        String first = "Bu birinchi gap bo'lib, u ancha uzun va batafsil yozilgan. ".repeat(4).trim();
        String second = "Endi ikkinchi qism keladi va u ham o'z gapi bilan tugaydi.";
        List<String> pieces = YandexTtsProvider.splitForSynthesis(first + " " + second);

        assertThat(pieces).hasSizeGreaterThan(1);
        assertThat(pieces).allSatisfy(piece -> {
            assertThat(piece.length()).isLessThanOrEqualTo(YandexTtsProvider.MAX_TEXT_CHARS);
            assertThat(piece).isNotBlank();
        });
        // Nothing lost, nothing reordered: the pieces re-join into the original words.
        assertThat(String.join(" ", pieces)).isEqualTo(first + " " + second);
    }

    @Test
    void textWithNoSentenceEndSplitsOnWordBoundaries() {
        String words = "so'z ".repeat(80).trim(); // 399 chars, no terminator anywhere
        List<String> pieces = YandexTtsProvider.splitForSynthesis(words);

        assertThat(pieces).hasSize(2);
        assertThat(pieces).allSatisfy(piece ->
                assertThat(piece.length()).isLessThanOrEqualTo(YandexTtsProvider.MAX_TEXT_CHARS));
        assertThat(String.join(" ", pieces)).isEqualTo(words);
    }

    @Test
    void unbrokenRunLongerThanTheLimitIsHardCutRatherThanLooping() {
        String run = "a".repeat(YandexTtsProvider.MAX_TEXT_CHARS * 2 + 10);
        List<String> pieces = YandexTtsProvider.splitForSynthesis(run);

        assertThat(pieces).hasSize(3);
        assertThat(String.join("", pieces)).isEqualTo(run);
    }

    /**
     * The mood a turn asks for is matched against the roles the voice declares in
     * {@code voice-agent.tts.yandex.voice-roles}: a role Yandex would refuse fails the
     * whole request, and the caller then hears nothing at all.
     */
    private static YandexTtsProvider providerWithRoles() {
        YandexTtsProperties yandex = new YandexTtsProperties(
                "key", null, "alena", Map.of("uz-UZ", "nigora"),
                Map.of("nigora", List.of(),
                        "alena", List.of("neutral", "good"),
                        "omazh", List.of("neutral", "evil")),
                "tts.api.cloud.yandex.net", 443, 8000, 0);
        return new YandexTtsProvider(new TtsProperties(
                true, "yandex", "uz-UZ", null, null, null, yandex, null, null, null));
    }

    @Test
    void voiceWithNoRolesIsSpokenWithoutOne() {
        YandexTtsProvider provider = providerWithRoles();

        assertThat(provider.roleFor("nigora", EffectiveVoiceSettings.NONE.withRole("friendly"))).isNull();
        assertThat(provider.roleFor("nigora", EffectiveVoiceSettings.NONE.withRole("neutral"))).isNull();
        // A voice the config does not mention is treated the same way.
        assertThat(provider.roleFor("zamira", EffectiveVoiceSettings.NONE.withRole("strict"))).isNull();
    }

    @Test
    void moodIsTranslatedToTheRoleTheVoiceActuallyHas() {
        YandexTtsProvider provider = providerWithRoles();

        assertThat(provider.roleFor("alena", EffectiveVoiceSettings.NONE.withRole("friendly"))).isEqualTo("good");
        assertThat(provider.roleFor("alena", EffectiveVoiceSettings.NONE.withRole("cheerful"))).isEqualTo("good");
        assertThat(provider.roleFor("alena", EffectiveVoiceSettings.NONE.withRole("neutral"))).isEqualTo("neutral");
        assertThat(provider.roleFor("omazh", EffectiveVoiceSettings.NONE.withRole("strict"))).isEqualTo("evil");
        // alena has no angry role at all — spoken plain rather than refused.
        assertThat(provider.roleFor("alena", EffectiveVoiceSettings.NONE.withRole("strict"))).isNull();
    }

    @Test
    void noMoodAskedForMeansNoRoleHint() {
        YandexTtsProvider provider = providerWithRoles();

        assertThat(provider.roleFor("alena", EffectiveVoiceSettings.NONE)).isNull();
        assertThat(provider.roleFor("alena", null)).isNull();
    }
}
