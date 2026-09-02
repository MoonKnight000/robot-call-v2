package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment;
import uz.murodjon.robotcallv2.agent.dialog.VoiceEmotionResolver.VoiceEmotion;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

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
                null, null, voiceSettings, emotionAdaptive, Map.of()
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
    void moodIsNamedWithoutRegardToTheVoiceThatWillSpeakIt() {
        DialogSession session = createSession("GREETING", "cheerful", "nigora", EffectiveVoiceSettings.NONE, true);

        EffectiveVoiceSettings settings = resolver.resolve(session);

        // Whether Nigora can speak it is the TTS provider's call (voice-agent.tts.yandex.voice-roles).
        assertThat(settings.role()).isEqualTo("cheerful");
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
    void everyEmotionIsNamedAsAMood() {
        assertThat(resolver.mapEmotionToRole(VoiceEmotion.CHEERFUL)).isEqualTo("cheerful");
        assertThat(resolver.mapEmotionToRole(VoiceEmotion.FRIENDLY)).isEqualTo("friendly");
        assertThat(resolver.mapEmotionToRole(VoiceEmotion.STRICT)).isEqualTo("strict");
        assertThat(resolver.mapEmotionToRole(VoiceEmotion.NEUTRAL)).isEqualTo("neutral");
    }

    @Test
    void sentimentDetectorCorrectlyIdentifiesSentiments() {
        assertThat(sentimentDetector.analyze("Nega menga tinimsiz telefon qilyapsiz, asabimga tegmang!")).isEqualTo(CustomerSentiment.FRUSTRATED);
        assertThat(sentimentDetector.analyze("Bu nima degani? Tushunmadim, qanaqa qarz?")).isEqualTo(CustomerSentiment.CONFUSED);
        assertThat(sentimentDetector.analyze("Eshitaman, kim bu?")).isEqualTo(CustomerSentiment.CONFUSED);
    }
}
