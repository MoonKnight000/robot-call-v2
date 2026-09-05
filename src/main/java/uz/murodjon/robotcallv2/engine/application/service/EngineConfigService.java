package uz.murodjon.robotcallv2.engine.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.agent.realtime.RealtimeProperties;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.engine.application.dto.EngineOptions;
import uz.murodjon.robotcallv2.engine.application.dto.UpdateEngineConfigRequest;
import uz.murodjon.robotcallv2.engine.application.port.input.EngineConfigUseCase;
import uz.murodjon.robotcallv2.engine.application.port.output.EngineConfigRepository;
import uz.murodjon.robotcallv2.engine.domain.entity.EffectiveEngineConfig;
import uz.murodjon.robotcallv2.engine.domain.entity.EngineConfig;
import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.engine.domain.service.EngineConfigValidator;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;

import java.util.List;

@Service
@Transactional
public class EngineConfigService implements EngineConfigUseCase {

    public static final List<String> PIPECAT_STT_OPTIONS = List.of(
            "deepgram", "soniox", "speechmatics", "yandex", "whisper"
    );
    public static final List<String> PIPECAT_LLM_OPTIONS = List.of(
            "claude-3-5-haiku", "claude-3-5-sonnet", "gemini-2.0-flash", "gpt-4o-mini", "groq-llama-3.3-70b"
    );
    public static final List<String> PIPECAT_TTS_OPTIONS = List.of(
            "cartesia", "elevenlabs", "yandex", "google-chirp"
    );

    private final EngineConfigRepository repository;
    private final CurrentCompany currentCompany;
    private final AuditService auditService;
    private final SttProviderSelector sttProviderSelector;
    private final TtsProviderSelector ttsProviderSelector;
    private final RealtimeProviderRegistry realtimeProviderRegistry;
    private final SttProperties sttProperties;
    private final TtsProperties ttsProperties;
    private final RealtimeProperties realtimeProperties;

    public EngineConfigService(EngineConfigRepository repository,
                               CurrentCompany currentCompany,
                               AuditService auditService,
                               SttProviderSelector sttProviderSelector,
                               TtsProviderSelector ttsProviderSelector,
                               RealtimeProviderRegistry realtimeProviderRegistry,
                               SttProperties sttProperties,
                               TtsProperties ttsProperties,
                               RealtimeProperties realtimeProperties) {
        this.repository = repository;
        this.currentCompany = currentCompany;
        this.auditService = auditService;
        this.sttProviderSelector = sttProviderSelector;
        this.ttsProviderSelector = ttsProviderSelector;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
        this.sttProperties = sttProperties;
        this.ttsProperties = ttsProperties;
        this.realtimeProperties = realtimeProperties;
    }

    @Override
    public EngineConfig findForCurrentCompany() {
        return repository.findByCompanyId(currentCompany.id());
    }

    @Override
    public EffectiveEngineConfig findEffectiveForCurrentCompany() {
        return findEffectiveByCompanyId(currentCompany.id());
    }

    @Override
    public EngineOptions findOptions() {
        return new EngineOptions(
                sttProviderSelector.names(),
                ttsProviderSelector.names(),
                realtimeProviderRegistry.names(),
                PIPECAT_STT_OPTIONS,
                PIPECAT_LLM_OPTIONS,
                PIPECAT_TTS_OPTIONS
        );
    }

    @Override
    public EngineConfig updateForCurrentCompany(UpdateEngineConfigRequest request) {
        PipelineMode mode = request.mode() != null ? request.mode() : PipelineMode.CASCADE;
        validate(mode, request);
        EngineConfig saved = repository.upsert(currentCompany.id(),
                EngineConfig.overrides(mode, request.sttProvider(), request.ttsProvider(),
                        request.realtimeProvider(), request.pipecatStt(), request.pipecatLlm(),
                        request.pipecatTts()));
        auditService.record("ENGINE_CONFIG_UPDATE", "engine_config",
                String.valueOf(saved.companyId()), mode.name());
        return saved;
    }

    @Override
    public EffectiveEngineConfig findEffectiveByCompanyId(long companyId) {
        EngineConfig config = repository.findByCompanyId(companyId);
        if (config == null) {
            return new EffectiveEngineConfig(PipelineMode.CASCADE,
                    sttProperties.provider(), ttsProperties.provider(), realtimeProperties.provider(),
                    null, null, null);
        }
        return new EffectiveEngineConfig(
                config.mode() != null ? config.mode() : PipelineMode.CASCADE,
                EngineConfigValidator.orDefault(config.sttProvider(), sttProperties.provider()),
                EngineConfigValidator.orDefault(config.ttsProvider(), ttsProperties.provider()),
                EngineConfigValidator.orDefault(config.realtimeProvider(), realtimeProperties.provider()),
                config.pipecatStt(),
                config.pipecatLlm(),
                config.pipecatTts());
    }

    private void validate(PipelineMode mode, UpdateEngineConfigRequest request) {
        if (mode == PipelineMode.REALTIME) {
            validateRealtime(request.realtimeProvider(), request);
            return;
        }
        if (EngineConfigValidator.isSet(request.sttProvider()) && !sttProviderSelector.exists(request.sttProvider())) {
            throw new ValidationException(ErrorCode.ENGINE_STT_PROVIDER_UNKNOWN,
                    request.sttProvider(), sttProviderSelector.names());
        }
        if (EngineConfigValidator.isSet(request.ttsProvider()) && !ttsProviderSelector.exists(request.ttsProvider())) {
            throw new ValidationException(ErrorCode.ENGINE_TTS_PROVIDER_UNKNOWN,
                    request.ttsProvider(), ttsProviderSelector.names());
        }
    }

    private void validateRealtime(String realtimeProvider, UpdateEngineConfigRequest request) {
        if (realtimeProviderRegistry.isEmpty()) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_NOT_AVAILABLE);
        }
        if (EngineConfigValidator.isSet(realtimeProvider) && !realtimeProviderRegistry.exists(realtimeProvider)) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_PROVIDER_UNKNOWN,
                    realtimeProvider, realtimeProviderRegistry.names());
        }
        if (!EngineConfigValidator.isSet(realtimeProvider) && realtimeProviderRegistry.findForCall(null) == null) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_PROVIDER_REQUIRED,
                    realtimeProviderRegistry.names());
        }
    }
}
