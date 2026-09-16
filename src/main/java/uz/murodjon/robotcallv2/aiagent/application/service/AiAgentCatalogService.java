package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aiagent.application.dto.EngineOptionsResponse;
import uz.murodjon.robotcallv2.aiagent.application.dto.TemplatePresetDto;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentCatalogUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentTemplates;

import java.util.List;

/** The agent form's choices: starting templates and the speech engines this build can run. */
@Service
public class AiAgentCatalogService implements AiAgentCatalogUseCase {

    /**
     * Sub-engines the pipecat build can be pointed at. Listed here rather than discovered
     * the way the cascade providers above them are: nothing in this process implements
     * them, so there is no registry to ask — the settings screen is offering names the
     * external pipecat service understands.
     */
    private static final List<String> PIPECAT_STT_OPTIONS = List.of(
            "deepgram", "gemini", "soniox", "speechmatics", "yandex", "whisper"
    );
    private static final List<String> PIPECAT_LLM_OPTIONS = List.of(
            "claude-3-5-haiku", "claude-3-5-sonnet", "gemini-3.5-flash-lite", "gemini-3.8-flash",
            "gpt-4o-mini", "groq-llama-3.3-70b"
    );
    private static final List<String> PIPECAT_TTS_OPTIONS = List.of(
            "cartesia", "elevenlabs", "gemini", "yandex", "google-chirp"
    );

    private final SttProviderSelector sttProviderSelector;
    private final TtsProviderSelector ttsProviderSelector;
    private final RealtimeProviderRegistry realtimeProviderRegistry;

    public AiAgentCatalogService(SttProviderSelector sttProviderSelector, TtsProviderSelector ttsProviderSelector,
                                 RealtimeProviderRegistry realtimeProviderRegistry) {
        this.sttProviderSelector = sttProviderSelector;
        this.ttsProviderSelector = ttsProviderSelector;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
    }

    @Override
    public List<TemplatePresetDto> findTemplates(String language) {
        return List.of(
                new TemplatePresetDto(AgentTemplate.BUSINESS, "Business Agent",
                        "General purpose business calls, lead qualification, and customer questions",
                        AiAgentTemplates.getPreset(AgentTemplate.BUSINESS, language)),
                new TemplatePresetDto(AgentTemplate.SUPPORT, "Support Agent",
                        "Customer support, ticket triage, and issue troubleshooting",
                        AiAgentTemplates.getPreset(AgentTemplate.SUPPORT, language)),
                new TemplatePresetDto(AgentTemplate.MEDICAL, "Medical Agent",
                        "Patient intake, appointment scheduling, and clinic front-desk workflows",
                        AiAgentTemplates.getPreset(AgentTemplate.MEDICAL, language)),
                new TemplatePresetDto(AgentTemplate.BLANK, "Blank Agent",
                        "Empty configuration to design your own custom agent behavior",
                        AiAgentTemplates.getPreset(AgentTemplate.BLANK, language))
        );
    }

    @Override
    public EngineOptionsResponse findEngineOptions() {
        return new EngineOptionsResponse(
                sttProviderSelector.names(),
                ttsProviderSelector.names(),
                realtimeProviderRegistry.names(),
                PIPECAT_STT_OPTIONS,
                PIPECAT_LLM_OPTIONS,
                PIPECAT_TTS_OPTIONS
        );
    }
}
