package uz.murodjon.uysotvoice.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.uysotvoice.agent.dialog.SentimentDetector.CustomerSentiment;
import uz.murodjon.uysotvoice.agent.dialog.VoiceEmotionResolver.VoiceEmotion;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.StageDef;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceEmotionResolverTest {

    private final SentimentDetector sentimentDetector = new SentimentDetector();
    private final VoiceEmotionResolver resolver = new VoiceEmotionResolver();

    private static DialogSession createSession(String stageId, String stageEmotion, String ttsVoice,
                                               EffectiveVoiceSettings voiceSettings, boolean emotionAdaptive) {
        StageDef stage = new StageDef(stageId, "Purpose of " + stageId, List.of(), List.of(), stageEmotion);
        ScenarioDefinition scenario = new ScenarioDefinition(
                List.of(stage),
                List.of(),
                List.of(),
                List.of(),
                "Role prompt",
                List.of(),
                "uz-UZ"
        );
        DialogSession session = new DialogSession(
                "chan-test", "uz-UZ", ttsVoice,
                new CallContext(Map.of(), "goal"),
                scenario, null, null, null, 1L, null, true, "Company",
                null, null, voiceSettings, emotionAdaptive
        );
        session.setState(stageId);
        return session;
    }

    @Test
    void customerFrustrationOverridesToFriendlyAndSlowerPace() {
        DialogSession session = createSession("DEBT_NOTICE", "strict", "zamira", EffectiveVoiceSettings.NONE, true);
        session.setLastCustomerSentiment(CustomerSentiment.FRUSTRATED);

        EffectiveVoiceSettings settings = resolver.resolve(session);

        assertThat(settings.role()).isEqualTo("friendly");
        assertThat(settings.speed()).isLessThan(1.0); // Slowed down for frustrated customer
    }

    @Test
    void customerConfusionSetsNeutralRoleAndSlightlySlowerPace() {
        DialogSession session = createSession("DEBT_NOTICE", "strict", "zamira", EffectiveVoiceSettings.NONE, true);
        session.setLastCustomerSentiment(CustomerSentiment.CONFUSED);

        EffectiveVoiceSettings settings = resolver.resolve(session);

        assertThat(settings.role()).isEqualTo("neutral");
        assertThat(settings.speed()).isEqualTo(0.96);
    }

    @Test
    void stageExplicitEmotionIsAppliedWhenCustomerIsNeutral() {
        DialogSession session = createSession("GREETING", "cheerful", "gulnoza", EffectiveVoiceSettings.NONE, true);
        session.setLastCustomerSentiment(CustomerSentiment.NEUTRAL);

        EffectiveVoiceSettings settings = resolver.resolve(session);

        assertThat(settings.role()).isEqualTo("cheerful");
        assertThat(settings.speed()).isNull();
    }

    @Test
    void defaultStageRoleFallbackWorksCorrectly() {
        // GREETING without explicit emotion should fallback to cheerful
        DialogSession greetingSession = createSession("GREETING", null, "gulnoza", EffectiveVoiceSettings.NONE, true);
        assertThat(resolver.determineEmotion(greetingSession)).isEqualTo(VoiceEmotion.CHEERFUL);

        // DEBT_NOTICE without explicit emotion should fallback to strict
        DialogSession debtSession = createSession("DEBT_NOTICE", null, "zamira", EffectiveVoiceSettings.NONE, true);
        assertThat(resolver.determineEmotion(debtSession)).isEqualTo(VoiceEmotion.STRICT);

        // ESCALATE without explicit emotion should fallback to friendly
        DialogSession escalateSession = createSession("ESCALATE", null, "zamira", EffectiveVoiceSettings.NONE, true);
        assertThat(resolver.determineEmotion(escalateSession)).isEqualTo(VoiceEmotion.FRIENDLY);
    }

    @Test
    void yandexNigoraNeverReceivesRole() {
        DialogSession session = createSession("GREETING", "cheerful", "nigora", EffectiveVoiceSettings.NONE, true);

        EffectiveVoiceSettings settings = resolver.resolve(session);

        // nigora does not support roles in SpeechKit v3
        assertThat(settings.role()).isNull();
    }

    @Test
    void disabledEmotionAdaptiveVoicePreservesBaseSpeedAndRole() {
        EffectiveVoiceSettings base = new EffectiveVoiceSettings("yandex", 1.2, 0.0, "strict");
        DialogSession session = createSession("GREETING", "cheerful", "zamira", base, false);
        session.setLastCustomerSentiment(CustomerSentiment.FRUSTRATED);

        EffectiveVoiceSettings settings = resolver.resolve(session);

        assertThat(settings.speed()).isEqualTo(1.2);
        assertThat(settings.role()).isEqualTo("friendly");
    }

    @Test
    void russianVoicesMapToYandexRussianRoles() {
        assertThat(resolver.mapEmotionToRole("alena", VoiceEmotion.CHEERFUL)).isEqualTo("good");
        assertThat(resolver.mapEmotionToRole("filipp", VoiceEmotion.FRIENDLY)).isEqualTo("good");
        assertThat(resolver.mapEmotionToRole("alena", VoiceEmotion.STRICT)).isEqualTo("evil");
        assertThat(resolver.mapEmotionToRole("alena", VoiceEmotion.NEUTRAL)).isEqualTo("neutral");
    }

    @Test
    void sentimentDetectorCorrectlyIdentifiesSentiments() {
        assertThat(sentimentDetector.analyze("Nega menga tinimsiz telefon qilyapsiz, asabimga tegmang!")).isEqualTo(CustomerSentiment.FRUSTRATED);
        assertThat(sentimentDetector.analyze("Bu nima degani? Tushunmadim, qanaqa qarz?")).isEqualTo(CustomerSentiment.CONFUSED);
        assertThat(sentimentDetector.analyze("Eshitaman, kim bu?")).isEqualTo(CustomerSentiment.CONFUSED);
    }
}
