package uz.murodjon.robotcallv2.aiagent.application.service;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProvider;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiAgentEngineGuardTest {

    private static final long COMPANY_ID = 1L;

    private RealtimeProviderRegistry realtimeProviderRegistry;
    private TtsVoiceUseCase ttsVoiceUseCase;
    private AiModelUseCase aiModelUseCase;
    private CompanyConfigUseCase companyConfigUseCase;
    private AiAgentEngineGuard guard;

    @BeforeEach
    void setUp() {
        realtimeProviderRegistry = mock(RealtimeProviderRegistry.class);
        ttsVoiceUseCase = mock(TtsVoiceUseCase.class);
        aiModelUseCase = mock(AiModelUseCase.class);
        companyConfigUseCase = mock(CompanyConfigUseCase.class);
        guard = new AiAgentEngineGuard(mock(SttProviderSelector.class), mock(TtsProviderSelector.class),
                realtimeProviderRegistry, ttsVoiceUseCase, aiModelUseCase, companyConfigUseCase);
    }

    /** Every agent needs endCall at least, so an engine that cannot call tools cannot run one. */
    @Test
    void aRealtimeEngineThatCannotCallToolsIsRefused() {
        when(realtimeProviderRegistry.exists("moshi")).thenReturn(true);
        when(realtimeProviderRegistry.lacksToolCalling("moshi")).thenReturn(true);

        assertRefused(() -> guard.requireKnownRealtimeProvider(PipelineMode.REALTIME, " moshi "),
                ErrorCode.ENGINE_REALTIME_TOOLS_UNSUPPORTED);
    }

    @Test
    void theDefaultEngineIsHeldToTheSameToolRule() {
        RealtimeProvider moshi = mock(RealtimeProvider.class);
        when(moshi.name()).thenReturn("moshi");
        when(realtimeProviderRegistry.findForCall(null)).thenReturn(moshi);
        when(realtimeProviderRegistry.lacksToolCalling("moshi")).thenReturn(true);

        assertRefused(() -> guard.requireKnownRealtimeProvider(PipelineMode.REALTIME, "  "),
                ErrorCode.ENGINE_REALTIME_TOOLS_UNSUPPORTED);
    }

    @Test
    void aToolCallingRealtimeEngineIsAcceptedTrimmed() {
        when(realtimeProviderRegistry.exists("gemini")).thenReturn(true);

        assertThat(guard.requireKnownRealtimeProvider(PipelineMode.REALTIME, " gemini ")).isEqualTo("gemini");
    }

    @Test
    void realtimeInABuildWithNoEngineIsRefused() {
        when(realtimeProviderRegistry.isEmpty()).thenReturn(true);

        assertRefused(() -> guard.requireKnownRealtimeProvider(PipelineMode.REALTIME, "gemini"),
                ErrorCode.ENGINE_REALTIME_NOT_AVAILABLE);
    }

    @Test
    void anUnknownRealtimeEngineIsRefused() {
        when(realtimeProviderRegistry.names()).thenReturn(List.of("gemini"));

        assertRefused(() -> guard.requireKnownRealtimeProvider(PipelineMode.REALTIME, "openai"),
                ErrorCode.ENGINE_REALTIME_PROVIDER_UNKNOWN);
    }

    @Test
    void noRealtimeEngineAndNoConfiguredDefaultIsRefused() {
        assertRefused(() -> guard.requireKnownRealtimeProvider(PipelineMode.REALTIME, null),
                ErrorCode.ENGINE_REALTIME_PROVIDER_REQUIRED);
    }

    /** Only read after a switch back to REALTIME, which checks it again. */
    @Test
    void aCascadeAgentKeepsItsRealtimeEngineUnchecked() {
        assertThat(guard.requireKnownRealtimeProvider(PipelineMode.CASCADE, " moshi ")).isEqualTo("moshi");
        verifyNoInteractions(realtimeProviderRegistry);
    }

    /** Both ids are REALTIME rows, so the pipeline check alone would let this pair through. */
    @Test
    void aRealtimeModelOfAnotherEngineIsRefused() {
        when(aiModelUseCase.isSelectable(AiModelKind.LLM, PipelineMode.REALTIME, "gpt-realtime-2.1")).thenReturn(true);
        when(aiModelUseCase.find("gpt-realtime-2.1")).thenReturn(
                new AiModel("gpt-realtime-2.1", AiModelKind.LLM, "openai", PipelineMode.REALTIME, "GPT Realtime"));

        assertRefused(() -> guard.requireKnownModel(PipelineMode.REALTIME, "gemini", "gpt-realtime-2.1"),
                ErrorCode.REALTIME_MODEL_PROVIDER_MISMATCH);
    }

    /** The fast model answers text turns, so a realtime agent picks it from the cascade catalog. */
    @Test
    void theFastModelIsCheckedAgainstTheCascadeCatalog() {
        when(aiModelUseCase.isSelectable(AiModelKind.LLM, PipelineMode.CASCADE, "gemini-3.5-flash-lite")).thenReturn(true);

        assertThat(guard.requireKnownFastModel(" gemini-3.5-flash-lite ")).isEqualTo("gemini-3.5-flash-lite");
    }

    @Test
    void aLiveModelIsNotAFastModel() {
        assertRefused(() -> guard.requireKnownFastModel("gemini-3.8-live-extended-thinking"),
                ErrorCode.AI_MODEL_UNKNOWN);
    }

    @Test
    void aModelThisBuildCannotRunIsUnknown() {
        assertRefused(() -> guard.requireKnownModel(PipelineMode.CASCADE, null, "gpt-9"), ErrorCode.AI_MODEL_UNKNOWN);
    }

    /** Reported as the provider's model even when that provider has no key here, not as unknown. */
    @Test
    void anSttModelOfAnotherProviderIsAMismatchNotUnknown() {
        when(aiModelUseCase.find("nova-3")).thenReturn(new AiModel("nova-3", AiModelKind.STT, "deepgram", null, "Nova 3"));

        assertRefused(() -> guard.requireKnownSttModel("yandex", "nova-3"), ErrorCode.STT_MODEL_PROVIDER_MISMATCH);
    }

    @Test
    void onlyAnSttModelTheBuildCanReachIsAccepted() {
        when(aiModelUseCase.findSelectableByKindAndMode(AiModelKind.STT, null)).thenReturn(
                List.of(new AiModel("general", AiModelKind.STT, "yandex", null, "General")));

        assertThat(guard.requireKnownSttModel("yandex", " general ")).isEqualTo("general");
        assertRefused(() -> guard.requireKnownSttModel("yandex", "nova-9"), ErrorCode.STT_MODEL_UNKNOWN);
    }

    @Test
    void aVoiceForALanguageItDoesNotSpeakIsRefused() {
        when(companyConfigUseCase.resolveLanguage(COMPANY_ID, "ru")).thenReturn("ru-RU");
        when(ttsVoiceUseCase.isSelectable(PipelineMode.CASCADE, "nigora")).thenReturn(true);
        when(ttsVoiceUseCase.find("nigora")).thenReturn(new TtsVoice("nigora", "yandex", "uz-UZ", "Nigora", "Nigora", null));

        assertRefused(() -> guard.requireKnownVoicePerLanguage(COMPANY_ID, PipelineMode.CASCADE, Map.of("ru", "nigora")),
                ErrorCode.TTS_VOICE_LANGUAGE_MISMATCH);
    }

    private static void assertRefused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }
}
