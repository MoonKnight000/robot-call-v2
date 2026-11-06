package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.application.dto.ModelPriceResponse;
import uz.murodjon.robotcallv2.billing.application.dto.UpsertModelPriceRequest;

import java.util.List;

/** The platform's per-model price list (platform admins only). */
public interface ModelPriceUseCase {

    List<ModelPriceResponse> findAll();

    /** Creates or replaces the row for {@code model}; {@code companyId} is only the audit trail's. */
    ModelPriceResponse upsert(long companyId, String model, UpsertModelPriceRequest request);

    void delete(long companyId, String model);
}
