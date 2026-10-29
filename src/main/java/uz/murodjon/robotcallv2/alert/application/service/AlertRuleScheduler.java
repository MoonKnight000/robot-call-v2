package uz.murodjon.robotcallv2.alert.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.alert.application.port.output.AlertRuleRepository;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.service.AlertMessageFormatter;
import uz.murodjon.robotcallv2.alert.domain.service.AlertRuleEvaluator;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Instant;

/**
 * Evaluates every enabled alert rule of a company that has alerting switched on, every few minutes, on one instance at a time. A breached
 * rule notifies its company ({@code ALERT_TRIGGERED}: the panel bell plus whichever channels
 * the company's notification settings send that type to), then stays quiet for its cooldown.
 * One rule failing to evaluate does not stop the others.
 */
@Service
public class AlertRuleScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleScheduler.class);

    private final AlertRuleRepository repository;
    private final AlertMetricReader metricReader;
    private final NotificationUseCase notificationUseCase;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;

    public AlertRuleScheduler(AlertRuleRepository repository, AlertMetricReader metricReader,
                              NotificationUseCase notificationUseCase, ClusterJobLock clusterJobLock, Clock clock) {
        this.repository = repository;
        this.metricReader = metricReader;
        this.notificationUseCase = notificationUseCase;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${voice-agent.alert-rules.check-ms:300000}", initialDelay = 60_000)
    public void evaluateRules() {
        clusterJobLock.runExclusively("alert-rules", this::evaluateAll);
    }

    void evaluateAll() {
        Instant now = clock.instant();
        for (AlertRule rule : repository.findEnabled()) {
            try {
                evaluate(rule, now);
            } catch (RuntimeException e) {
                log.warn("Alert rule {} of company {} could not be evaluated: {}",
                        rule.id(), rule.companyId(), e.getMessage());
            }
        }
    }

    private void evaluate(AlertRule rule, Instant now) {
        AlertMetricValue value = metricReader.findValue(rule, now);
        boolean fire = AlertRuleEvaluator.isBreached(rule, value) && !AlertRuleEvaluator.isCoolingDown(rule, now);
        if (fire) {
            notificationUseCase.notify(rule.companyId(), NotificationType.ALERT_TRIGGERED, rule.name(),
                    AlertMessageFormatter.formatMessage(rule, value), null);
            log.info("Alert rule {} of company {} fired: {} = {} over {} sample(s)",
                    rule.id(), rule.companyId(), rule.metric(), value.value(), value.sampleSize());
        }
        repository.recordEvaluation(rule.id(), value.value(), now, fire);
    }
}
