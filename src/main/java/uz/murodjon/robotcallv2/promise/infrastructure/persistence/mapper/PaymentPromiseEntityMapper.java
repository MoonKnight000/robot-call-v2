package uz.murodjon.robotcallv2.promise.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseStats;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.entity.PaymentPromiseEntity;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.entity.PaymentPromiseSettingsEntity;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.repository.PaymentPromiseStatsProjection;

@Component
public class PaymentPromiseEntityMapper {

    public PaymentPromise toPaymentPromise(PaymentPromiseEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaymentPromise(entity.getId(), entity.getCompanyId(), entity.getCallAttemptId(),
                entity.getTargetId(), entity.getPhone(), entity.getPromisedDate(), entity.getPromisedAmount(),
                entity.getStatus(), entity.getReminderSentAt(), entity.getResolvedAt(), entity.getCreatedAt());
    }

    public PaymentPromiseStats toPaymentPromiseStats(PaymentPromiseStatsProjection row) {
        return PaymentPromiseStats.of(row.getTotal(), row.getOpen(), row.getKept(), row.getBroken(),
                row.getCancelled(), row.getPromisedAmount(), row.getKeptAmount());
    }

    public PaymentPromiseSettings toPaymentPromiseSettings(PaymentPromiseSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaymentPromiseSettings(entity.getCompanyId(), entity.isReminderEnabled(),
                entity.getReminderDaysBefore(), entity.getReminderTemplate(), entity.getGraceDays(),
                entity.isRecallOnBroken(), entity.getPaymentGoalKey());
    }

    public void applySettings(PaymentPromiseSettingsEntity entity, PaymentPromiseSettings settings) {
        entity.setReminderEnabled(settings.reminderEnabled());
        entity.setReminderDaysBefore(settings.reminderDaysBefore());
        entity.setReminderTemplate(settings.reminderTemplate());
        entity.setGraceDays(settings.graceDays());
        entity.setRecallOnBroken(settings.recallOnBroken());
        entity.setPaymentGoalKey(settings.paymentGoalKey());
    }
}
