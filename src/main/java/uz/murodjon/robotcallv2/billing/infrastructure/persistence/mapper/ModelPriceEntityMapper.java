package uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingLlmRates;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelPrice;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingModelPriceEntity;

import java.time.Instant;

@Component
public class ModelPriceEntityMapper {

    public ModelPrice toModelPrice(BillingModelPriceEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ModelPrice(entity.getModel(),
                new BillingLlmRates(entity.getInputPerMillionUzs(), entity.getOutputPerMillionUzs(),
                        entity.getCachedPerMillionUzs()),
                entity.getRealtimePerMinuteUzs(), entity.getNote(), entity.getUpdatedAt());
    }

    public BillingModelPriceEntity toBillingModelPriceEntity(ModelPrice price, Instant updatedAt) {
        BillingModelPriceEntity entity = new BillingModelPriceEntity();
        entity.setModel(price.model());
        entity.setInputPerMillionUzs(price.llm().promptPerMillionUzs());
        entity.setOutputPerMillionUzs(price.llm().completionPerMillionUzs());
        entity.setCachedPerMillionUzs(price.llm().cachedPromptPerMillionUzs());
        entity.setRealtimePerMinuteUzs(price.realtimePerMinuteUzs());
        entity.setNote(price.note());
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}
