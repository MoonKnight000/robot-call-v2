package uz.murodjon.robotcallv2.aiagent.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aiagent.application.dto.EngineOptionsResponse;
import uz.murodjon.robotcallv2.aiagent.application.dto.TemplatePresetDto;
import uz.murodjon.robotcallv2.aiagent.domain.entity.EngineProfile;
import uz.murodjon.robotcallv2.aiagent.domain.entity.TemplateDefaults;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentTemplates;
import uz.murodjon.robotcallv2.aiagent.infrastructure.config.EngineProfileProperties;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAgentCatalogServiceTest {

    private static final EngineProfile FAST = new EngineProfile("fast", "Fast", "cheap", "yandex", "general",
            "gemini-3.5-flash-lite", "gemini-3.5-flash-lite", 0.3, 200);
    private static final EngineProfile QUALITY = new EngineProfile("quality", "Quality", "best", "yandex", "general",
            "gpt-5.6-sol", "gemini-3.5-flash-lite", 0.3, 300);

    private AiAgentEngineGuard engineGuard;
    private SttProviderSelector sttProviderSelector;
    private TtsProviderSelector ttsProviderSelector;
    private RealtimeProviderRegistry realtimeProviderRegistry;
    private AiAgentCatalogService service;

    @BeforeEach
    void setUp() {
        sttProviderSelector = mock(SttProviderSelector.class);
        ttsProviderSelector = mock(TtsProviderSelector.class);
        realtimeProviderRegistry = mock(RealtimeProviderRegistry.class);
        engineGuard = mock(AiAgentEngineGuard.class);
        service = new AiAgentCatalogService(sttProviderSelector, ttsProviderSelector, realtimeProviderRegistry,
                new EngineProfileProperties(List.of(FAST, QUALITY)), engineGuard);
    }

    @Test
    void findEngineProfilesLeavesOutAProfileThisBuildCannotRun() {
        when(engineGuard.requireKnownModel(PipelineMode.CASCADE, null, "gpt-5.6-sol"))
                .thenThrow(new ValidationException(ErrorCode.AI_MODEL_UNKNOWN, "gpt-5.6-sol", List.of()));

        assertThat(service.findEngineProfiles()).containsExactly(FAST);
    }

    @Test
    void requireEngineProfileNamesWhatIsOfferedWhenTheIdIsUnknown() {
        assertThat(service.requireEngineProfile("fast")).isEqualTo(FAST);
        assertThatThrownBy(() -> service.requireEngineProfile("missing"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void findEngineOptionsOffersTheEnginesThisBuildHas() {
        when(sttProviderSelector.names()).thenReturn(List.of("yandex", "gemini"));
        when(ttsProviderSelector.names()).thenReturn(List.of("yandex"));
        when(realtimeProviderRegistry.names()).thenReturn(List.of("gemini"));

        EngineOptionsResponse options = service.findEngineOptions();

        assertThat(options.stt()).containsExactly("yandex", "gemini");
        assertThat(options.tts()).containsExactly("yandex");
        assertThat(options.realtime()).containsExactly("gemini");
    }

    @Test
    void aBuildWithoutRealtimeEnginesOffersNone() {
        when(realtimeProviderRegistry.names()).thenReturn(List.of());

        assertThat(service.findEngineOptions().realtime()).isEmpty();
    }

    /** Nothing in this process runs the pipecat sub-engines, so their names are a fixed list, not discovered. */
    @Test
    void thePipecatSubEnginesAreOfferedWhateverTheBuildHas() {
        EngineOptionsResponse options = service.findEngineOptions();

        assertThat(options.pipecatStt()).contains("deepgram", "yandex");
        assertThat(options.pipecatLlm()).isNotEmpty();
        assertThat(options.pipecatTts()).contains("cartesia", "yandex");
    }

    @Test
    void findTemplatesOffersThePresetsWithBlankLast() {
        assertThat(service.findTemplates("uz-UZ")).extracting(TemplatePresetDto::templateId)
                .containsExactly(AgentTemplate.BUSINESS, AgentTemplate.SUPPORT, AgentTemplate.MEDICAL,
                        AgentTemplate.DEBT_REMINDER, AgentTemplate.LEAD_QUALIFICATION, AgentTemplate.SURVEY,
                        AgentTemplate.BLANK);
    }

    @Test
    void findTemplatesSpeaksTheRequestedLanguage() {
        TemplatePresetDto russian = service.findTemplates("ru-RU").getFirst();
        TemplatePresetDto uzbek = service.findTemplates("uz-UZ").getFirst();

        assertThat(russian.defaults()).isEqualTo(AiAgentTemplates.getPreset(AgentTemplate.BUSINESS, "ru-RU"));
        assertThat(russian.defaults().firstMessage()).isNotEqualTo(uzbek.defaults().firstMessage());
    }

    @Test
    void theBlankTemplateStartsWithNothing() {
        TemplateDefaults blank = service.findTemplates("uz-UZ").getLast().defaults();

        assertThat(blank.firstMessage()).isEmpty();
        assertThat(blank.systemPrompt()).isEmpty();
        assertThat(blank.dataNeeded()).isEmpty();
    }
}
