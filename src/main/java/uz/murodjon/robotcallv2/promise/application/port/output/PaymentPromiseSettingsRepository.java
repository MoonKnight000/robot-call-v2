package uz.murodjon.robotcallv2.promise.application.port.output;

import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;

public interface PaymentPromiseSettingsRepository {

    /** Null when the company never saved any. */
    PaymentPromiseSettings findByCompanyId(long companyId);

    PaymentPromiseSettings upsert(long companyId, PaymentPromiseSettings settings);
}
