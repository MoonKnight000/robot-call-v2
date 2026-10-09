package uz.murodjon.robotcallv2.alert.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.alert.application.dto.AlertRulePreviewResponse;
import uz.murodjon.robotcallv2.alert.application.dto.SaveAlertRuleRequest;
import uz.murodjon.robotcallv2.alert.application.mapper.AlertRuleMapper;
import uz.murodjon.robotcallv2.alert.application.port.input.AlertRuleUseCase;
import uz.murodjon.robotcallv2.alert.application.port.output.AlertRuleRepository;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRuleFilter;
import uz.murodjon.robotcallv2.alert.domain.service.AlertMessageFormatter;
import uz.murodjon.robotcallv2.alert.domain.service.AlertRuleEvaluator;
import uz.murodjon.robotcallv2.alert.domain.service.AlertRuleValidator;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Clock;

@Service
public class AlertRuleService implements AlertRuleUseCase {

    private final AlertRuleRepository repository;
    private final AlertRuleMapper mapper;
    private final AlertMetricReader metricReader;
    private final AiAgentUseCase aiAgentUseCase;
    private final Clock clock;

    public AlertRuleService(AlertRuleRepository repository, AlertRuleMapper mapper, AlertMetricReader metricReader,
                            AiAgentUseCase aiAgentUseCase, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.metricReader = metricReader;
        this.aiAgentUseCase = aiAgentUseCase;
        this.clock = clock;
    }

    @Override
    public AlertRule createRule(long companyId, SaveAlertRuleRequest request) {
        AlertRule rule = validated(companyId, request);
        if (repository.countByCompanyId(companyId) >= AlertRuleValidator.MAX_RULES_PER_COMPANY) {
            throw new ConflictException(ErrorCode.ALERT_RULE_LIMIT_REACHED, AlertRuleValidator.MAX_RULES_PER_COMPANY);
        }
        return repository.create(rule);
    }

    @Override
    public AlertRule updateRule(long companyId, long id, SaveAlertRuleRequest request) {
        AlertRule updated = repository.update(companyId, id, validated(companyId, request));
        if (updated == null) {
            throw new NotFoundException(ErrorCode.ALERT_RULE_NOT_FOUND, id);
        }
        return updated;
    }

    @Override
    public void deleteRule(long companyId, long id) {
        findRule(companyId, id);
        repository.deleteByCompanyIdAndId(companyId, id);
    }

    @Override
    public AlertRule findRule(long companyId, long id) {
        AlertRule rule = repository.findByCompanyIdAndId(companyId, id);
        if (rule == null) {
            throw new NotFoundException(ErrorCode.ALERT_RULE_NOT_FOUND, id);
        }
        return rule;
    }

    @Override
    public PageableData<AlertRule> filterRules(long companyId, AlertRuleFilter filter) {
        return repository.findPageByCompanyId(companyId, filter);
    }

    @Override
    public AlertRulePreviewResponse previewRule(long companyId, long id) {
        AlertRule rule = findRule(companyId, id);
        AlertMetricValue value = metricReader.findValue(rule, clock.instant());
        return new AlertRulePreviewResponse(value.value(), value.sampleSize(),
                AlertRuleEvaluator.isBreached(rule, value),
                value.value() == null ? null : AlertMessageFormatter.formatMessage(rule, value));
    }

    /** The request as a rule, checked — including that its agent is the company's own. */
    private AlertRule validated(long companyId, SaveAlertRuleRequest request) {
        AlertRule rule = mapper.toAlertRule(companyId, request);
        AlertRuleValidator.validate(rule);
        if (rule.aiAgentId() != null) {
            aiAgentUseCase.requireAgent(companyId, rule.aiAgentId());
        }
        return rule;
    }
}
