package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aiagent.application.dto.EngineOptionsResponse;
import uz.murodjon.robotcallv2.aiagent.application.dto.TemplatePresetDto;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentCatalogUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.EngineProfile;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentTemplates;
import uz.murodjon.robotcallv2.aiagent.infrastructure.config.EngineProfileProperties;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

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
    private final EngineProfileProperties engineProfileProperties;
    private final AiAgentEngineGuard engineGuard;

    public AiAgentCatalogService(SttProviderSelector sttProviderSelector, TtsProviderSelector ttsProviderSelector,
                                 RealtimeProviderRegistry realtimeProviderRegistry,
                                 EngineProfileProperties engineProfileProperties, AiAgentEngineGuard engineGuard) {
        this.sttProviderSelector = sttProviderSelector;
        this.ttsProviderSelector = ttsProviderSelector;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
        this.engineProfileProperties = engineProfileProperties;
        this.engineGuard = engineGuard;
    }

    @Override
    public List<EngineProfile> findEngineProfiles() {
        return engineProfileProperties.items().stream().filter(this::isRunnable).toList();
    }

    @Override
    public EngineProfile requireEngineProfile(String id) {
        List<EngineProfile> offered = findEngineProfiles();
        return offered.stream()
                .filter(profile -> profile.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ValidationException(ErrorCode.ENGINE_PROFILE_UNKNOWN, id,
                        offered.stream().map(EngineProfile::id).toList()));
    }

    /**
     * Whether every engine and model the profile names is one this build has. Asked of the same
     * guard a create runs, so a profile listed here is never refused when it is applied.
     */
    private boolean isRunnable(EngineProfile profile) {
        try {
            engineGuard.requireKnownSttProvider(profile.sttProvider());
            engineGuard.requireKnownSttModel(profile.sttProvider(), profile.sttModel());
            engineGuard.requireKnownModel(PipelineMode.CASCADE, null, profile.llmModel());
            engineGuard.requireKnownFastModel(profile.fastLlmModel());
            return true;
        } catch (ValidationException unavailable) {
            return false;
        }
    }

    @Override
    public List<TemplatePresetDto> findTemplates(String language) {
        return List.of(
                new TemplatePresetDto(AgentTemplate.APPOINTMENT_BOOKING, "Appointment Booking Agent",
                        "Patient and client scheduling, availability check, and calendar booking",
                        AiAgentTemplates.getPreset(AgentTemplate.APPOINTMENT_BOOKING, language)),
                new TemplatePresetDto(AgentTemplate.SUPPORT, "Customer Support Agent",
                        "Inbound helpdesk, troubleshooting, FAQ, and ticketing",
                        AiAgentTemplates.getPreset(AgentTemplate.SUPPORT, language)),
                new TemplatePresetDto(AgentTemplate.LEAD_QUALIFICATION, "Lead Qualification Agent",
                        "BANT qualification, customer intent verification, and product demo scheduling",
                        AiAgentTemplates.getPreset(AgentTemplate.LEAD_QUALIFICATION, language)),
                new TemplatePresetDto(AgentTemplate.DEBT_REMINDER, "Debt Collection Agent",
                        "Past-due payment reminders, negotiation, and promise-to-pay date recording",
                        AiAgentTemplates.getPreset(AgentTemplate.DEBT_REMINDER, language)),
                new TemplatePresetDto(AgentTemplate.INBOUND_RECEPTIONIST, "Virtual Receptionist Agent",
                        "Front desk reception, department routing, and taking messages",
                        AiAgentTemplates.getPreset(AgentTemplate.INBOUND_RECEPTIONIST, language)),
                new TemplatePresetDto(AgentTemplate.SURVEY, "CSAT / NPS Survey Agent",
                        "Customer satisfaction assessment and actionable feedback gathering",
                        AiAgentTemplates.getPreset(AgentTemplate.SURVEY, language)),
                new TemplatePresetDto(AgentTemplate.MEDICAL, "Clinic Patient Intake Agent",
                        "Healthcare front-desk triage, non-emergency symptom collection, and specialist booking",
                        AiAgentTemplates.getPreset(AgentTemplate.MEDICAL, language)),
                new TemplatePresetDto(AgentTemplate.ORDER_TRACKING, "Order Tracking Agent",
                        "E-commerce package status lookup, delivery date inquiries, and address updates",
                        AiAgentTemplates.getPreset(AgentTemplate.ORDER_TRACKING, language)),
                new TemplatePresetDto(AgentTemplate.BLANK, "Blank Agent",
                        "Empty configuration to design your own custom agent behavior from scratch",
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
