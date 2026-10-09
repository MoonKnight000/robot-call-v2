package uz.murodjon.robotcallv2.promise.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseRepository;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseSettingsRepository;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;

import java.time.Instant;

/** What the scheduler and a hand-marked status share: the settings, and breaking a promise. */
@Component
public class PaymentPromiseFollowUp {

    private static final Logger log = LoggerFactory.getLogger(PaymentPromiseFollowUp.class);

    private final PaymentPromiseRepository repository;
    private final PaymentPromiseSettingsRepository settingsRepository;
    private final CampaignTargetUseCase campaignTargetUseCase;

    public PaymentPromiseFollowUp(PaymentPromiseRepository repository,
                                  PaymentPromiseSettingsRepository settingsRepository,
                                  CampaignTargetUseCase campaignTargetUseCase) {
        this.repository = repository;
        this.settingsRepository = settingsRepository;
        this.campaignTargetUseCase = campaignTargetUseCase;
    }

    public PaymentPromiseSettings findSettings(long companyId) {
        PaymentPromiseSettings settings = settingsRepository.findByCompanyId(companyId);
        return settings != null ? settings : PaymentPromiseSettings.defaultsFor(companyId);
    }

    /**
     * Marks the promise broken and, when the company asked for it, puts its campaign target back
     * in the queue now (the campaign's dial window and the contact policy still apply). A target
     * whose campaign has finished is queued but not dialled until the campaign runs again.
     *
     * @return false when the promise was no longer open, and nothing was done
     */
    public boolean breakPromise(PaymentPromise promise, PaymentPromiseSettings settings, Instant now) {
        if (!repository.updateStatusIfOpen(promise.id(), PaymentPromiseStatus.BROKEN, now)) {
            return false;
        }
        if (settings.recallOnBroken() && promise.targetId() != null) {
            campaignTargetUseCase.scheduleCallback(promise.companyId(), promise.targetId(), now);
            log.info("Promise {} broken; target {} queued for a call back", promise.id(), promise.targetId());
        }
        return true;
    }
}
