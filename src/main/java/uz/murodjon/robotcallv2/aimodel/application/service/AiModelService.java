package uz.murodjon.robotcallv2.aimodel.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.application.port.output.AiModelRepository;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;

import java.util.List;

/**
 * The models an agent may be set to run on — the same answer to the form that offers them
 * and to the check that accepts them, which is the whole point of the type: a model id
 * used to be free text, and a typo was only discovered by the call that failed on it.
 *
 * <p>"Selectable" is narrower than "in the catalog", for the same reason it is with
 * voices: the agent's pipeline decides which family of ids has any meaning, and inside
 * that family only a provider this build can actually reach counts. Offering the rest
 * would let a setting be saved that looks right and silently falls back at call time.
 */
@Service
public class AiModelService implements AiModelUseCase {

    private final AiModelRepository repository;
    private final RealtimeProviderRegistry realtimeProviderRegistry;
    private final SttProviderSelector sttProviderSelector;
    private final TtsProviderSelector ttsProviderSelector;
    /** {@code spring.ai.model.chat} — which chat provider the single ChatModel bean is. */
    private final String chatProvider;

    public AiModelService(AiModelRepository repository,
                          RealtimeProviderRegistry realtimeProviderRegistry,
                          SttProviderSelector sttProviderSelector,
                          TtsProviderSelector ttsProviderSelector,
                          @Value("${spring.ai.model.chat:}") String chatProvider) {
        this.repository = repository;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
        this.sttProviderSelector = sttProviderSelector;
        this.ttsProviderSelector = ttsProviderSelector;
        this.chatProvider = chatProvider;
    }

    @Override
    public List<AiModel> findSelectableByKindAndMode(AiModelKind kind, PipelineMode mode) {
        return repository.findByKindAndMode(kind, mode).stream().filter(this::reachable).toList();
    }

    @Override
    public AiModel find(String id) {
        return repository.findById(id);
    }

    @Override
    public boolean isSelectable(AiModelKind kind, PipelineMode mode, String id) {
        AiModel model = repository.findById(id);
        return model != null && model.kind() == kind
                && (mode == null || model.mode() == mode)
                && reachable(model);
    }

    @Override
    public List<String> findSelectableIds(AiModelKind kind, PipelineMode mode) {
        return findSelectableByKindAndMode(kind, mode).stream().map(AiModel::id).toList();
    }

    /**
     * Whether this build can serve the model at all: a realtime engine has to be
     * registered, a cascade chat model has to belong to the one chat provider the
     * application was wired with — a Gemini key does not answer for an OpenAI model id — and
     * a recognizer or synthesizer has to belong to a speech provider that is a bean here,
     * which it only is when its API key is configured.
     */
    private boolean reachable(AiModel model) {
        return switch (model.kind()) {
            case STT -> sttProviderSelector.exists(model.provider());
            case TTS -> ttsProviderSelector.exists(model.provider());
            case LLM -> model.mode() == PipelineMode.REALTIME
                    ? realtimeProviderRegistry.exists(model.provider())
                    : model.provider().equalsIgnoreCase(chatProvider);
        };
    }
}
