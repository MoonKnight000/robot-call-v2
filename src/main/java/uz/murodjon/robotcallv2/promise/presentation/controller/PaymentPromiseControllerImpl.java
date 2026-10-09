package uz.murodjon.robotcallv2.promise.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.promise.application.dto.UpdatePaymentPromiseSettingsRequest;
import uz.murodjon.robotcallv2.promise.application.dto.UpdatePaymentPromiseStatusRequest;
import uz.murodjon.robotcallv2.promise.application.port.input.PaymentPromiseUseCase;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseFilter;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseStats;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.time.LocalDate;

@RestController
public class PaymentPromiseControllerImpl implements PaymentPromiseController {

    private final PaymentPromiseUseCase paymentPromiseUseCase;

    public PaymentPromiseControllerImpl(PaymentPromiseUseCase paymentPromiseUseCase) {
        this.paymentPromiseUseCase = paymentPromiseUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<PaymentPromise>>> filter(long companyId,
                                                                            PaymentPromiseFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(paymentPromiseUseCase.filterPromises(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<PaymentPromise>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(paymentPromiseUseCase.findPromise(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<PaymentPromiseStats>> stats(long companyId, LocalDate from, LocalDate to) {
        return ResponseEntity.ok(ResponseData.ok(paymentPromiseUseCase.findStats(companyId, from, to)));
    }

    @Override
    public ResponseEntity<ResponseData<PaymentPromise>> updateStatus(long companyId, long id,
                                                                     UpdatePaymentPromiseStatusRequest request) {
        return ResponseEntity.ok(ResponseData.ok(paymentPromiseUseCase.updateStatus(companyId, id, request.status())));
    }

    @Override
    public ResponseEntity<ResponseData<PaymentPromiseSettings>> getSettings(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(paymentPromiseUseCase.findSettings(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<PaymentPromiseSettings>> updateSettings(
            long companyId, UpdatePaymentPromiseSettingsRequest request) {
        return ResponseEntity.ok(ResponseData.ok(paymentPromiseUseCase.updateSettings(companyId, request)));
    }
}
