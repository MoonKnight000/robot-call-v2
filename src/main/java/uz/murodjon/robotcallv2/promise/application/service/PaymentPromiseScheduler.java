package uz.murodjon.robotcallv2.promise.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.conversion.application.port.input.ConversionUseCase;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseRepository;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;
import uz.murodjon.robotcallv2.promise.domain.service.PaymentPromiseRules;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks every open promise, on one instance at a time, and for each — in this order:
 * <ol>
 *   <li>a payment reported for the number since the call → {@code KEPT};</li>
 *   <li>the date and the grace days behind us → {@code BROKEN}, and the target called back
 *       when the company has that on;</li>
 *   <li>the reminder day, in daytime → the reminder SMS, once.</li>
 * </ol>
 * Dates are the company's own. One promise failing does not stop the rest.
 */
@Service
public class PaymentPromiseScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentPromiseScheduler.class);

    private static final int BATCH = 200;
    /** A payment made during the call itself (the agent sent a link) is still this promise's. */
    static final Duration PAYMENT_LOOKBACK = Duration.ofHours(1);

    private final PaymentPromiseRepository repository;
    private final PaymentPromiseFollowUp followUp;
    private final ConversionUseCase conversionUseCase;
    private final SmsUseCase smsUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;

    public PaymentPromiseScheduler(PaymentPromiseRepository repository, PaymentPromiseFollowUp followUp,
                                   ConversionUseCase conversionUseCase, SmsUseCase smsUseCase,
                                   CompanyConfigUseCase companyConfigUseCase, ClusterJobLock clusterJobLock,
                                   Clock clock) {
        this.repository = repository;
        this.followUp = followUp;
        this.conversionUseCase = conversionUseCase;
        this.smsUseCase = smsUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${voice-agent.payment-promises.check-ms:900000}", initialDelay = 90_000)
    public void followUpPromises() {
        clusterJobLock.runExclusively("payment-promises", this::followUpAll);
    }

    void followUpAll() {
        Instant now = clock.instant();
        Map<Long, PaymentPromiseSettings> settingsByCompany = new HashMap<>();
        Map<Long, ZoneId> zoneByCompany = new HashMap<>();
        long afterId = 0;
        List<PaymentPromise> batch;
        do {
            batch = repository.findOpenAfterId(afterId, BATCH);
            for (PaymentPromise promise : batch) {
                afterId = promise.id();
                try {
                    PaymentPromiseSettings settings =
                            settingsByCompany.computeIfAbsent(promise.companyId(), followUp::findSettings);
                    ZoneId zone = zoneByCompany.computeIfAbsent(promise.companyId(), this::zoneOf);
                    followUp(promise, settings, now.atZone(zone), now);
                } catch (RuntimeException e) {
                    log.warn("Promise {} of company {} could not be followed up: {}",
                            promise.id(), promise.companyId(), e.getMessage());
                }
            }
        } while (batch.size() == BATCH);
    }

    private void followUp(PaymentPromise promise, PaymentPromiseSettings settings, ZonedDateTime local,
                          Instant now) {
        if (conversionUseCase.existsEventByPhoneSince(promise.companyId(), promise.phone(),
                settings.paymentGoalKey(), promise.createdAt().minus(PAYMENT_LOOKBACK))) {
            if (repository.updateStatusIfOpen(promise.id(), PaymentPromiseStatus.KEPT, now)) {
                log.info("Promise {} kept: a payment was reported for the number", promise.id());
            }
            return;
        }
        if (PaymentPromiseRules.isOverdue(promise, settings.graceDays(), local.toLocalDate())) {
            followUp.breakPromise(promise, settings, now);
            return;
        }
        if (settings.reminderEnabled() && smsUseCase.isEnabled()
                && PaymentPromiseRules.isReminderDue(promise, settings.reminderDaysBefore(), local.toLocalDate())
                && PaymentPromiseRules.isReminderHour(local.toLocalTime())) {
            String text = PaymentPromiseRules.renderReminder(settings.reminderTemplate(), promise);
            if (smsUseCase.sendSms(promise.companyId(), new SmsSendRequest(promise.phone(), text))) {
                repository.markReminded(promise.id(), now);
                log.info("Promise {} reminded by SMS", promise.id());
            }
        }
    }

    private ZoneId zoneOf(long companyId) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return config != null ? config.zoneId() : clock.getZone();
    }
}
