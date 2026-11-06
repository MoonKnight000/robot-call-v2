package uz.murodjon.robotcallv2.billing.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.dto.ModelPriceResponse;
import uz.murodjon.robotcallv2.billing.application.dto.UpsertModelPriceRequest;
import uz.murodjon.robotcallv2.billing.application.port.input.ModelPriceUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.ModelPriceRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingLlmRates;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelPrice;
import uz.murodjon.robotcallv2.billing.domain.service.ModelPriceMatcher;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The per-model price list: Gemini, GPT and the rest each cost what their provider charges,
 * so one common token rate over- or under-charged every call that ran on another model.
 */
@Service
public class ModelPriceService implements ModelPriceUseCase {

    private final ModelPriceRepository repository;
    private final AuditUseCase audit;

    public ModelPriceService(ModelPriceRepository repository, AuditUseCase audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelPriceResponse> findAll() {
        return repository.findAll().stream().map(ModelPriceService::toResponse).toList();
    }

    @Override
    @Transactional
    public ModelPriceResponse upsert(long companyId, String model, UpsertModelPriceRequest request) {
        String id = requireValidId(model);
        ModelPrice saved = repository.upsert(new ModelPrice(id,
                new BillingLlmRates(request.inputPerMillionUzs(), request.outputPerMillionUzs(),
                        request.cachedPerMillionUzs()),
                request.realtimePerMinuteUzs(), request.note(), null));
        audit.record(companyId, "MODEL_PRICE_UPSERT", "billing_model_price", id,
                "in=" + request.inputPerMillionUzs() + " out=" + request.outputPerMillionUzs()
                        + " cached=" + request.cachedPerMillionUzs() + " rt=" + request.realtimePerMinuteUzs());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(long companyId, String model) {
        String id = requireValidId(model);
        if (!repository.deleteByModel(id)) {
            throw new NotFoundException(ErrorCode.MODEL_PRICE_NOT_FOUND, id);
        }
        audit.record(companyId, "MODEL_PRICE_DELETE", "billing_model_price", id, null);
    }

    /**
     * Looks up a provider-reported model's prices — the settlement and the model picker read
     * through this. One read of the (small) table per call; null for a model without a row.
     */
    public Function<String, ModelPrice> findPriceLookup() {
        Map<String, ModelPrice> byId = repository.findAll().stream()
                .collect(Collectors.toMap(ModelPrice::model, Function.identity()));
        return reported -> {
            String match = ModelPriceMatcher.findMatch(reported, byId.keySet());
            return match == null ? null : byId.get(match);
        };
    }

    private static String requireValidId(String model) {
        String id = ModelPriceMatcher.normalize(model);
        if (!ModelPriceMatcher.isValidId(id)) {
            throw new ValidationException(ErrorCode.MODEL_PRICE_MODEL_INVALID, model);
        }
        return id;
    }

    private static ModelPriceResponse toResponse(ModelPrice price) {
        return new ModelPriceResponse(price.model(), price.llm().promptPerMillionUzs(),
                price.llm().completionPerMillionUzs(), price.llm().cachedPromptPerMillionUzs(),
                price.realtimePerMinuteUzs(), price.note(), price.updatedAt());
    }
}
