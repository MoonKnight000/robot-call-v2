package uz.murodjon.robotcallv2.inbound.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.inbound.application.dto.SaveCallerRuleRequest;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundCallerRuleUseCase;
import uz.murodjon.robotcallv2.inbound.application.port.output.InboundCallerRuleRepository;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRuleFilter;
import uz.murodjon.robotcallv2.inbound.domain.service.CallerRuleMatcher;
import uz.murodjon.robotcallv2.inbound.domain.service.InboundRouteValidator;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

/**
 * Inbound caller rules — the blacklist and VIP list (docs/INBOUND-PBX-PLAN.md §9). Kept
 * apart from do-not-call on purpose: that is a client's refusal of our calls, this is the
 * company's refusal of a caller's.
 */
@Service
public class InboundCallerRuleService implements InboundCallerRuleUseCase {

    private final InboundCallerRuleRepository repository;
    private final AuditUseCase auditService;

    public InboundCallerRuleService(InboundCallerRuleRepository repository, AuditUseCase auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Override
    public InboundCallerRule create(long companyId, SaveCallerRuleRequest request) {
        InboundCallerRule rule = toRule(request);
        if (repository.existsByPrefix(companyId, rule.phonePrefix(), null)) {
            throw new ConflictException(ErrorCode.CALLER_RULE_PREFIX_EXISTS, rule.phonePrefix());
        }
        long id = repository.create(companyId, rule);
        auditService.record(companyId, "CALLER_RULE_CREATE", "inbound_caller_rule", String.valueOf(id),
                rule.phonePrefix() + " " + rule.action());
        return requireRule(companyId, id);
    }

    @Override
    public InboundCallerRule update(long companyId, long id, SaveCallerRuleRequest request) {
        requireRule(companyId, id);
        InboundCallerRule rule = toRule(request);
        if (repository.existsByPrefix(companyId, rule.phonePrefix(), id)) {
            throw new ConflictException(ErrorCode.CALLER_RULE_PREFIX_EXISTS, rule.phonePrefix());
        }
        repository.update(companyId, id, rule);
        auditService.record(companyId, "CALLER_RULE_UPDATE", "inbound_caller_rule", String.valueOf(id),
                rule.phonePrefix() + " " + rule.action());
        return requireRule(companyId, id);
    }

    @Override
    public void delete(long companyId, long id) {
        requireRule(companyId, id);
        repository.delete(companyId, id);
        auditService.record(companyId, "CALLER_RULE_DELETE", "inbound_caller_rule", String.valueOf(id), null);
    }

    @Override
    public PageableData<InboundCallerRule> list(long companyId, InboundCallerRuleFilter filter) {
        return PageableData.of(repository.findAll(companyId, filter), filter.pageOrDefault(), filter.sizeOrDefault(),
                repository.count(companyId));
    }

    @Override
    public InboundCallerRule findMatchingRule(long companyId, String callerNumber) {
        return CallerRuleMatcher.findMatch(repository.findByCompanyId(companyId), callerNumber);
    }

    private InboundCallerRule requireRule(long companyId, long id) {
        InboundCallerRule rule = repository.find(companyId, id);
        if (rule == null) {
            throw new NotFoundException(ErrorCode.CALLER_RULE_NOT_FOUND, id);
        }
        return rule;
    }

    private static InboundCallerRule toRule(SaveCallerRuleRequest request) {
        String prefix = request.phonePrefix().trim();
        if (!InboundCallerRule.ANONYMOUS.equalsIgnoreCase(prefix)) {
            prefix = prefix.startsWith("+") ? prefix.substring(1) : prefix;
        } else {
            prefix = InboundCallerRule.ANONYMOUS;
        }
        InboundRouteValidator.validateCallerRule(prefix, request.action(), request.message());
        return new InboundCallerRule(0, 0, prefix, request.action(),
                request.message() == null || request.message().isBlank() ? null : request.message().trim(),
                request.note(), null);
    }
}
