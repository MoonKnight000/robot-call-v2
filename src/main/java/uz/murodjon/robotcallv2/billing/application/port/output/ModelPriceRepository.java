package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.ModelPrice;

import java.util.List;
import java.util.Optional;

/** The per-model price list; platform-wide, not per company. */
public interface ModelPriceRepository {

    /** Every row, by model id. */
    List<ModelPrice> findAll();

    Optional<ModelPrice> findByModel(String model);

    /** Inserts or replaces the row for {@code price.model()}; {@code updatedAt} is stamped here. */
    ModelPrice upsert(ModelPrice price);

    /** @return false when there was no such row */
    boolean deleteByModel(String model);
}
