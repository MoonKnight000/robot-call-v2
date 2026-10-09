package uz.murodjon.robotcallv2.promise.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseSettingsRepository;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.entity.PaymentPromiseSettingsEntity;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.mapper.PaymentPromiseEntityMapper;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.repository.PaymentPromiseSettingsJpaRepository;

@Component
public class PaymentPromiseSettingsRepositoryAdapter implements PaymentPromiseSettingsRepository {

    private final PaymentPromiseSettingsJpaRepository jpaRepository;
    private final PaymentPromiseEntityMapper mapper;

    public PaymentPromiseSettingsRepositoryAdapter(PaymentPromiseSettingsJpaRepository jpaRepository,
                                                   PaymentPromiseEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public PaymentPromiseSettings findByCompanyId(long companyId) {
        return jpaRepository.findById(companyId).map(mapper::toPaymentPromiseSettings).orElse(null);
    }

    @Override
    public PaymentPromiseSettings upsert(long companyId, PaymentPromiseSettings settings) {
        PaymentPromiseSettingsEntity entity = jpaRepository.findById(companyId).orElseGet(() -> {
            PaymentPromiseSettingsEntity created = new PaymentPromiseSettingsEntity();
            created.setCompanyId(companyId);
            return created;
        });
        mapper.applySettings(entity, settings);
        return mapper.toPaymentPromiseSettings(jpaRepository.save(entity));
    }
}
