package uz.murodjon.robotcallv2.aimodel.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.application.port.output.AiModelRepository;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.engine.application.service.EngineConfigService;
import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

import java.util.List;

/**
 * The models a company may be set to run on — the same answer to the form that offers
 * them and to the check that accepts them, which is the whole point of the type: a model
 * id used to be free text, and a typo was only discovered by the call that failed on it.
 *
 * <p>"Selectable" is narrower than "in the catalog", for the same reason it is with
 * voices: the company's engine decides which family of ids has any meaning, and inside
 * that family only a provider this build can actually reach counts. Offering the rest
 * would let a setting be saved that looks right and silently falls back at call time.
 */
@Service
public class AiModelService implements AiModelUseCase {

    private final AiModelRepository repository;
    private final EngineConfigService engineConfigService;
    private final RealtimeProviderRegistry realtimeProviderRegistry;
    /** {@code spring.ai.model.chat} — which chat provider the single ChatModel bean is. */
    private final String chatProvider;

    public AiModelService(AiModelRepository repository, EngineConfigService engineConfigService,
                          RealtimeProviderRegistry realtimeProviderRegistry,
                          @Value("${spring.ai.model.chat:}") String chatProvider) {
        this.repository = repository;
        this.engineConfigService = engineConfigService;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
        this.chatProvider = chatProvider;
    }

    @Override
    public List<AiModel> findSelectableByCompanyId(long companyId) {
        PipelineMode mode = modeOf(companyId);
        return repository.findByMode(mode).stream()
                .filter(model -> reachable(mode, model))
                .toList();
    }

    @Override
    public boolean isSelectable(long companyId, String id) {
        AiModel model = repository.findById(id);
        if (model == null) {
            return false;
        }
        PipelineMode mode = modeOf(companyId);
        return model.mode() == mode && reachable(mode, model);
    }

    @Override
    public List<String> findSelectableIds(long companyId) {
        return findSelectableByCompanyId(companyId).stream().map(AiModel::id).toList();
    }

    private PipelineMode modeOf(long companyId) {
        return engineConfigService.findEffectiveByCompanyId(companyId).mode();
    }

    /**
     * Whether this build can serve the model at all: a realtime engine has to be
     * registered, and a cascade model has to belong to the one chat provider the
     * application was wired with — a Gemini key does not answer for a Groq model id.
     */
    private boolean reachable(PipelineMode mode, AiModel model) {
        return mode == PipelineMode.REALTIME
                ? realtimeProviderRegistry.exists(model.provider())
                : model.provider().equalsIgnoreCase(chatProvider);
    }
}
