package uz.murodjon.robotcallv2.billing.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;

public record TopupRequest(
        @NotNull(message = "Summa kiritilishi shart")
        @Min(value = 1000, message = "Minimal summa 1 000 so'm")
        Long amountUzs,

        @NotNull(message = "To'lov usuli tanlanishi shart")
        PaymentMethod paymentMethod
) {
}
