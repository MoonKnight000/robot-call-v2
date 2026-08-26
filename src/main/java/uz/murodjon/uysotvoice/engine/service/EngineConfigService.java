package uz.murodjon.uysotvoice.engine.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.realtime.RealtimeProperties;
import uz.murodjon.uysotvoice.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.uysotvoice.agent.stt.SttProperties;
import uz.murodjon.uysotvoice.agent.stt.SttProviderSelector;
import uz.murodjon.uysotvoice.agent.tts.TtsProperties;
import uz.murodjon.uysotvoice.agent.tts.TtsProviderSelector;
import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.engine.domain.EffectiveEngineConfig;
import uz.murodjon.uysotvoice.engine.domain.EngineConfig;
import uz.murodjon.uysotvoice.engine.dto.EngineOptions;
import uz.murodjon.uysotvoice.engine.dto.UpdateEngineConfigRequest;
import uz.murodjon.uysotvoice.engine.enums.PipelineMode;
import uz.murodjon.uysotvoice.engine.repository.EngineConfigRepository;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

/**
 * Per-company speech-engine choice (§11 settings) — which STT and TTS a company's calls
 * run on, or which realtime engine replaces both. {@link #findEffectiveByCompanyId} is
 * the runtime hot path the call path reads once per call; the rest is admin CRUD.
 *
 * <p>A provider id is validated here, against the providers actually registered in this
 * build, so a bad value is a {@code 400} at save time rather than a call that quietly
 * speaks through the wrong vendor.
 */
@Service
public class EngineConfigService {

    private final EngineConfigRepository repository;
    private final AuditService auditService;
    private final CurrentCompany currentCompany;
    private final SttProviderSelector sttProviderSelector;
    private final TtsProviderSelector ttsProviderSelector;
    private final RealtimeProviderRegistry realtimeProviderRegistry;
    private final SttProperties sttProperties;
    private final TtsProperties ttsProperties;
    private final RealtimeProperties realtimeProperties;

    public EngineConfigService(EngineConfigRepository repository, AuditService auditService,
                               CurrentCompany currentCompany, SttProviderSelector sttProviderSelector,
                               TtsProviderSelector ttsProviderSelector,
                               RealtimeProviderRegistry realtimeProviderRegistry, SttProperties sttProperties,
                               TtsProperties ttsProperties, RealtimeProperties realtimeProperties) {
        this.repository = repository;
        this.auditService = auditService;
        this.currentCompany = currentCompany;
        this.sttProviderSelector = sttProviderSelector;
        this.ttsProviderSelector = ttsProviderSelector;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
        this.sttProperties = sttProperties;
        this.ttsProperties = ttsProperties;
        this.realtimeProperties = realtimeProperties;
    }

    /** The current company's choice, or {@code null} if it has never chosen — meaning the process defaults. */
    public EngineConfig findForCurrentCompany() {
        return repository.findByCompanyId(currentCompany.id());
    }

    /**
     * The current company's choice merged over the process defaults, for the settings
     * screen — the same resolution the call path does, so what the screen shows is what a
     * call would run on rather than a second guess at the fallback rules.
     */
    public EffectiveEngineConfig findEffectiveForCurrentCompany() {
        return findEffectiveByCompanyId(currentCompany.id());
    }

    /** What this build can be set to — the closed list the settings screen offers. */
    public EngineOptions findOptions() {
        // Realtime comes back empty on a build with no engine wired: offering a mode
        // nothing implements would let a company pick one its calls cannot run.
        return new EngineOptions(sttProviderSelector.names(), ttsProviderSelector.names(),
                realtimeProviderRegistry.names());
    }

    /** Full replace: a field left out of the request clears that choice back to the process default. */
    public EngineConfig updateForCurrentCompany(UpdateEngineConfigRequest request) {
        PipelineMode mode = request.mode() != null ? request.mode() : PipelineMode.CASCADE;
        validate(mode, request);
        EngineConfig saved = repository.upsert(currentCompany.id(),
                EngineConfig.overrides(mode, request.sttProvider(), request.ttsProvider(),
                        request.realtimeProvider()));
        auditService.record("ENGINE_CONFIG_UPDATE", "engine_config",
                String.valueOf(saved.companyId()), mode.name());
        return saved;
    }

    /**
     * {@code companyId}'s choice merged over the process defaults — read once per call
     * and reused for the whole call. Never throws: a company with no row simply gets the
     * defaults, which is exactly how every call behaved before this setting existed.
     */
    public EffectiveEngineConfig findEffectiveByCompanyId(long companyId) {
        EngineConfig config = repository.findByCompanyId(companyId);
        if (config == null) {
            return new EffectiveEngineConfig(PipelineMode.CASCADE,
                    sttProperties.provider(), ttsProperties.provider(), realtimeProperties.provider());
        }
        return new EffectiveEngineConfig(
                config.mode() != null ? config.mode() : PipelineMode.CASCADE,
                orDefault(config.sttProvider(), sttProperties.provider()),
                orDefault(config.ttsProvider(), ttsProperties.provider()),
                orDefault(config.realtimeProvider(), realtimeProperties.provider()));
    }

    /**
     * Only the fields the chosen mode actually reads are checked. The others are stored
     * as sent: a company switching to REALTIME and back should find its cascade providers
     * where it left them, not cleared because they were unused for a while.
     */
    private void validate(PipelineMode mode, UpdateEngineConfigRequest request) {
        if (mode == PipelineMode.REALTIME) {
            validateRealtime(request.realtimeProvider());
            return;
        }
        if (isSet(request.sttProvider()) && !sttProviderSelector.exists(request.sttProvider())) {
            throw new ValidationException(ErrorCode.ENGINE_STT_PROVIDER_UNKNOWN,
                    request.sttProvider(), sttProviderSelector.names());
        }
        if (isSet(request.ttsProvider()) && !ttsProviderSelector.exists(request.ttsProvider())) {
            throw new ValidationException(ErrorCode.ENGINE_TTS_PROVIDER_UNKNOWN,
                    request.ttsProvider(), ttsProviderSelector.names());
        }
    }

    /**
     * A company may only switch to REALTIME if this build can actually run it: an engine
     * registered at all, and — once one is named — that name among them. Leaving the name
     * out is allowed and falls back to {@code voice-agent.realtime.provider}, which the
     * registry has already resolved.
     */
    private void validateRealtime(String realtimeProvider) {
        if (realtimeProviderRegistry.isEmpty()) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_NOT_AVAILABLE);
        }
        if (isSet(realtimeProvider) && !realtimeProviderRegistry.exists(realtimeProvider)) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_PROVIDER_UNKNOWN,
                    realtimeProvider, realtimeProviderRegistry.names());
        }
        if (!isSet(realtimeProvider) && realtimeProviderRegistry.findForCall(null) == null) {
            throw new ValidationException(ErrorCode.ENGINE_REALTIME_PROVIDER_REQUIRED,
                    realtimeProviderRegistry.names());
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String orDefault(String chosen, String fallback) {
        return isSet(chosen) ? chosen : fallback;
    }
}
