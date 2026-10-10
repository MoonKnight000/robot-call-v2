package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceEmotionResolverTest {

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
        DialogSession session = DialogSession.builder("chan-test", "uz-UZ")
                .ttsVoice(ttsVoice)
                .context(new CallContext(Map.of(), "goal"))
                .scenario(scenario)
                .callAttemptId(1L)
                .companyId(1L)
                .companyName("Company")
                .voiceSettings(voiceSettings)
                .emotionAdaptiveVoice(emotionAdaptive)
                .build();
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
    void aStageThatNamesNoEmotionIsSpokenNeutrally() {
        // The stage name is not a mood. Inferring one from it swung a single call through
        // cheerful (GREETING), strict (DEBT_NOTICE) and back to cheerful (CLOSING), which
        // is heard as acting rather than as a person.
        DialogSession greetingSession = createSession("GREETING", null, "gulnoza", EffectiveVoiceSettings.NONE, true);
        assertThat(resolver.determineEmotion(greetingSession)).isEqualTo(VoiceEmotion.NEUTRAL);

        DialogSession debtSession = createSession("DEBT_NOTICE", null, "zamira", EffectiveVoiceSettings.NONE, true);
        assertThat(resolver.determineEmotion(debtSession)).isEqualTo(VoiceEmotion.NEUTRAL);

        DialogSession escalateSession = createSession("ESCALATE", null, "zamira", EffectiveVoiceSettings.NONE, true);
        assertThat(resolver.determineEmotion(escalateSession)).isEqualTo(VoiceEmotion.NEUTRAL);
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
    void agentWithNullCompanySpeedDoesNotThrowNpe() {
        DialogSession session = createSession("GREETING", "cheerful", "nigora", EffectiveVoiceSettings.NONE, true);
        uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent agent =
                uz.murodjon.robotcallv2.aiagent.AiAgentFixtures.agent(1L, 1L, 1L, "uz-UZ", "nigora");
        session = DialogSession.builder("chan-test", "uz-UZ")
                .ttsVoice("nigora")
                .scenario(session.scenario())
                .agent(agent)
                .voiceSettings(EffectiveVoiceSettings.NONE)
                .emotionAdaptiveVoice(true)
                .build();
        session.setState("GREETING");

        EffectiveVoiceSettings settings = resolver.resolve(session);

        assertThat(settings.speed()).isNull();
        assertThat(settings.role()).isEqualTo("cheerful");
    }

    @Test
    void isRealtimeModelIdentifiesWebSocketLiveModelsCorrectly() {
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("gemini-3.8-live-extended-thinking")).isTrue();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("gemini-3.8-live")).isTrue();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("gpt-realtime-2.1")).isTrue();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("qwen-omni-turbo")).isTrue();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("moshi")).isTrue();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("gemini-2.5-flash-native-audio-latest")).isTrue();

        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("gemini-3.8-flash")).isFalse();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("gemini-2.5-pro")).isFalse();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("gpt-5.6-sol")).isFalse();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel(null)).isFalse();
        assertThat(uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel.isRealtimeModel("")).isFalse();
    }
}
