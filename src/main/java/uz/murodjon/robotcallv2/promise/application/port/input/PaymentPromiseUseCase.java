package uz.murodjon.robotcallv2.promise.application.port.input;

import uz.murodjon.robotcallv2.promise.application.dto.UpdatePaymentPromiseSettingsRequest;
import uz.murodjon.robotcallv2.promise.domain.entity.*;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** Promises to pay that calls recorded, and how the company follows them up. */
public interface PaymentPromiseUseCase {

    PageableData<PaymentPromise> filterPromises(long companyId, PaymentPromiseFilter filter);

    PaymentPromise findPromise(long companyId, long id);

    /** Promises dated {@code from}–{@code to} (inclusive) by outcome, with the kept-promise rate. */
    PaymentPromiseStats findStats(long companyId, LocalDate from, LocalDate to);

    /**
     * Marks an open promise by hand — for a payment no system reported. {@code BROKEN} does what
     * the deadline would: the target is called back when the company has that switched on.
     */
    PaymentPromise updateStatus(long companyId, long id, PaymentPromiseStatus status);

    PaymentPromiseSettings findSettings(long companyId);

    PaymentPromiseSettings updateSettings(long companyId, UpdatePaymentPromiseSettingsRequest request);

    /** Each number's kept and broken promises made since {@code since}; numbers with none are left out. */
    Map<String, PaymentPromiseTally> findTalliesByPhones(long companyId, Collection<String> phones, Instant since);
}
