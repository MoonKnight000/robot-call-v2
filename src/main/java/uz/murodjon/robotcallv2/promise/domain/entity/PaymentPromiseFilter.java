package uz.murodjon.robotcallv2.promise.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.time.LocalDate;
import java.util.LinkedHashMap;

/**
 * The promises' page. {@code promisedFrom}/{@code promisedTo} bound the promised date, both
 * inclusive; every null field means "any".
 */
public record PaymentPromiseFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<PaymentPromiseTableField, Sort.Direction> orders,
        PaymentPromiseStatus status,
        String phone,
        LocalDate promisedFrom,
        LocalDate promisedTo
) implements FilterInterface<PaymentPromiseTableField> {

    public PaymentPromiseFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(PaymentPromiseTableField.PROMISED_DATE, Sort.Direction.ASC);
        }
    }
}
