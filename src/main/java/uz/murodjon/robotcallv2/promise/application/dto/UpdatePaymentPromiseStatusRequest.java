package uz.murodjon.robotcallv2.promise.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;

/** @param status {@code KEPT}, {@code BROKEN} or {@code CANCELLED} */
public record UpdatePaymentPromiseStatusRequest(@NotNull PaymentPromiseStatus status) {
}
