package uz.murodjon.robotcallv2.promise.application.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.domain.event.PaymentPromisedEvent;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseRepository;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Clock;

/**
 * Opens a promise when a call's result records one. Runs in the result's transaction: a promise
 * exists exactly when its result does. A newer promise from the same number replaces the open
 * ones before it, so the caller is reminded of one date, not two.
 */
@Component
public class PaymentPromiseRecorder {

    private final PaymentPromiseRepository repository;
    private final Clock clock;

    public PaymentPromiseRecorder(PaymentPromiseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @EventListener
    public void recordPromise(PaymentPromisedEvent event) {
        // The number as conversion events store it, so a reported payment finds this promise.
        String phone = PhoneNumbers.normalize(event.phone());
        if (phone == null || phone.isBlank()) {
            return;
        }
        repository.cancelOpenByCompanyIdAndPhone(event.companyId(), phone, event.callAttemptId(), clock.instant());
        repository.createIgnoringConflict(PaymentPromise.opened(event.companyId(), event.callAttemptId(),
                event.targetId(), phone, event.promisedDate(), event.promisedAmount()));
    }
}
