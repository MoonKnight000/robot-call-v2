package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.port.output.ModelPriceRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelPrice;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper.ModelPriceEntityMapper;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.BillingModelPriceJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class ModelPriceRepositoryAdapter implements ModelPriceRepository {

    private final BillingModelPriceJpaRepository jpaRepository;
    private final ModelPriceEntityMapper mapper;

    public ModelPriceRepositoryAdapter(BillingModelPriceJpaRepository jpaRepository, ModelPriceEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ModelPrice> findAll() {
        return jpaRepository.findAll(Sort.by("model")).stream().map(mapper::toModelPrice).toList();
    }

    @Override
    public Optional<ModelPrice> findByModel(String model) {
        return jpaRepository.findById(model).map(mapper::toModelPrice);
    }

    @Override
    public ModelPrice upsert(ModelPrice price) {
        return mapper.toModelPrice(jpaRepository.save(mapper.toBillingModelPriceEntity(price, Instant.now())));
    }

    @Override
    public boolean deleteByModel(String model) {
        if (!jpaRepository.existsById(model)) {
            return false;
        }
        jpaRepository.deleteById(model);
        return true;
    }
}
