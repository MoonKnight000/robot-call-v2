package uz.murodjon.robotcallv2.aiagent.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aiagent.application.dto.EngineOptionsResponse;
import uz.murodjon.robotcallv2.aiagent.application.dto.TemplatePresetDto;
import uz.murodjon.robotcallv2.aiagent.domain.entity.TemplateDefaults;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentTemplates;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAgentCatalogServiceTest {

    private SttProviderSelector sttProviderSelector;
    private TtsProviderSelector ttsProviderSelector;
    private RealtimeProviderRegistry realtimeProviderRegistry;
    private AiAgentCatalogService service;

    @BeforeEach
    void setUp() {
        sttProviderSelector = mock(SttProviderSelector.class);
        ttsProviderSelector = mock(TtsProviderSelector.class);
        realtimeProviderRegistry = mock(RealtimeProviderRegistry.class);
        service = new AiAgentCatalogService(sttProviderSelector, ttsProviderSelector, realtimeProviderRegistry);
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
                .containsExactly(AgentTemplate.BUSINESS, AgentTemplate.SUPPORT, AgentTemplate.MEDICAL, AgentTemplate.BLANK);
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
