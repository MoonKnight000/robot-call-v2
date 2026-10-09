package uz.murodjon.robotcallv2.wrapup.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.wrapup.application.dto.SaveWrapUpCodeRequest;
import uz.murodjon.robotcallv2.wrapup.application.dto.WrapUpCallRequest;
import uz.murodjon.robotcallv2.wrapup.application.port.input.WrapUpUseCase;
import uz.murodjon.robotcallv2.wrapup.application.port.output.CallWrapUpRepository;
import uz.murodjon.robotcallv2.wrapup.application.port.output.WrapUpCodeRepository;
import uz.murodjon.robotcallv2.wrapup.domain.entity.CallWrapUp;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCodeFilter;
import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpOutcome;
import uz.murodjon.robotcallv2.wrapup.domain.service.WrapUpValidator;

import java.time.Clock;
import java.util.Objects;

/**
 * Wrap-up codes and the calls closed with them (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.1).
 * A call placed for a campaign target (a preview card, §1.2) also settles that target.
 */
@Service
public class WrapUpService implements WrapUpUseCase {

    private final WrapUpCodeRepository wrapUpCodeRepository;
    private final CallWrapUpRepository callWrapUpRepository;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final CurrentUser currentUser;
    private final AuditUseCase auditUseCase;
    private final Clock clock;

    public WrapUpService(WrapUpCodeRepository wrapUpCodeRepository, CallWrapUpRepository callWrapUpRepository,
                         CallAttemptUseCase callAttemptUseCase, CampaignTargetUseCase campaignTargetUseCase,
                         OperatorPresenceUseCase operatorPresenceUseCase, CurrentUser currentUser, AuditUseCase auditUseCase,
                         Clock clock) {
        this.wrapUpCodeRepository = wrapUpCodeRepository;
        this.callWrapUpRepository = callWrapUpRepository;
        this.callAttemptUseCase = callAttemptUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.currentUser = currentUser;
        this.auditUseCase = auditUseCase;
        this.clock = clock;
    }

    @Override
    public WrapUpCode createCode(long companyId, SaveWrapUpCodeRequest request) {
        WrapUpCode saved = wrapUpCodeRepository.create(companyId, toCode(request));
        auditUseCase.record(companyId, "WRAP_UP_CODE_CREATE", "wrap_up_code", String.valueOf(saved.id()),
                saved.name() + " (" + saved.outcome() + ")");
        return saved;
    }

    @Override
    public WrapUpCode updateCode(long companyId, long id, SaveWrapUpCodeRequest request) {
        requireCode(companyId, id);
        WrapUpCode saved = wrapUpCodeRepository.update(companyId, id, toCode(request));
        auditUseCase.record(companyId, "WRAP_UP_CODE_UPDATE", "wrap_up_code", String.valueOf(id),
                saved.name() + " (" + saved.outcome() + ")");
        return saved;
    }

    /** Calls already closed with the code keep its name and outcome — they were copied in. */
    @Override
    public void deleteCode(long companyId, long id) {
        requireCode(companyId, id);
        wrapUpCodeRepository.delete(companyId, id);
        auditUseCase.record(companyId, "WRAP_UP_CODE_DELETE", "wrap_up_code", String.valueOf(id), null);
    }

    @Override
    public WrapUpCode requireCode(long companyId, long id) {
        WrapUpCode code = wrapUpCodeRepository.find(companyId, id);
        if (code == null) {
            throw new NotFoundException(ErrorCode.WRAP_UP_CODE_NOT_FOUND, id);
        }
        return code;
    }

    @Override
    public PageableData<WrapUpCode> listCodes(long companyId, WrapUpCodeFilter filter) {
        return PageableData.of(wrapUpCodeRepository.findAll(companyId, filter), filter.pageOrDefault(),
                filter.sizeOrDefault(), wrapUpCodeRepository.count(companyId, filter));
    }

    @Override
    public CallWrapUp recordCallWrapUp(long companyId, long callId, WrapUpCallRequest request) {
        long userId = currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
        CallAttempt call = callAttemptUseCase.findAttemptByCompanyIdAndId(companyId, callId);
        if (call == null) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, callId);
        }
        // Only the operator who talked to the client knows how it went.
        if (!Objects.equals(call.operatorUserId(), userId)) {
            throw new ForbiddenException(ErrorCode.WRAP_UP_NOT_OPERATORS_CALL, callId);
        }
        WrapUpCode code = requireCode(companyId, request.wrapUpCodeId());
        WrapUpValidator.validate(code, request.callbackAt(), clock.instant());
        String note = request.note() != null && !request.note().isBlank() ? request.note().trim() : null;
        CallWrapUp saved = callWrapUpRepository.upsert(companyId,
                CallWrapUp.of(callId, code, userId, note, request.callbackAt()));
        if (call.targetId() != CallAttempt.NO_TARGET) {
            campaignTargetUseCase.settleByOperator(companyId, call.targetId(), toTargetStatus(code.outcome()),
                    request.callbackAt());
        }
        operatorPresenceUseCase.finishWrapUp(userId);
        return saved;
    }

    private static TargetStatus toTargetStatus(WrapUpOutcome outcome) {
        return switch (outcome) {
            case DONE -> TargetStatus.DONE;
            case RETRY -> TargetStatus.PENDING;
            case EXHAUSTED -> TargetStatus.EXHAUSTED;
        };
    }

    private static WrapUpCode toCode(SaveWrapUpCodeRequest request) {
        return WrapUpCode.of(request.name().trim(), request.outcome(), request.requiresCallbackAtOrDefault(),
                request.enabledOrDefault());
    }
}
