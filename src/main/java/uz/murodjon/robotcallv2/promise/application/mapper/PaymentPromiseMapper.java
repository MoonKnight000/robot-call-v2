package uz.murodjon.robotcallv2.promise.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.promise.application.dto.UpdatePaymentPromiseSettingsRequest;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;

@Component
public class PaymentPromiseMapper {

    public PaymentPromiseSettings toPaymentPromiseSettings(long companyId, UpdatePaymentPromiseSettingsRequest request) {
        String template = request.reminderTemplate() != null
                ? request.reminderTemplate().trim() : PaymentPromiseSettings.DEFAULT_REMINDER_TEMPLATE;
        String goalKey = request.paymentGoalKey() != null && !request.paymentGoalKey().isBlank()
                ? request.paymentGoalKey().trim() : null;
        return new PaymentPromiseSettings(companyId, request.reminderEnabled(), request.reminderDaysBefore(),
                template, request.graceDays(), request.recallOnBroken(), goalKey);
    }
}
