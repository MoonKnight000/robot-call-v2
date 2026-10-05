package uz.murodjon.robotcallv2.missedcall.application.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.queue.OperatorFirstDialer;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.missedcall.application.dto.AssignMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.application.dto.MissedCallCallBackResponse;
import uz.murodjon.robotcallv2.missedcall.application.dto.UpdateMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.application.port.output.MissedCallRepository;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallFilter;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallOccurrence;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The operators' list of callers still owed a call (docs/INBOUND-PBX-PLAN.md §8.2): who
 * rang after hours, who gave up in the queue, who met no free operator, who left a
 * voicemail — one row per number, closed by itself once the number is reached.
 */
@Service
public class MissedCallService implements MissedCallUseCase {

    /** Automatic callbacks per missed call before it is left to a person. */
    private static final int MAX_AUTO_CALLBACK_ATTEMPTS = 3;
    private static final Duration AUTO_CALLBACK_RETRY = Duration.ofMinutes(15);
    private static final int MIN_PHONE_DIGITS = 3;

    private final MissedCallRepository repository;
    private final UserUseCase userUseCase;
    private final NotificationUseCase notificationService;
    private final ContactCenterEventUseCase contactCenterEventUseCase;
    private final CurrentUser currentUser;
    /** Lazily: the dialer belongs to the call path, which itself records missed calls here. */
    private final ObjectProvider<OperatorFirstDialer> operatorFirstDialer;
    private final AuditUseCase auditService;
    private final Clock clock;

    public MissedCallService(MissedCallRepository repository, UserUseCase userUseCase,
                             NotificationUseCase notificationService,
                             ContactCenterEventUseCase contactCenterEventUseCase, CurrentUser currentUser,
                             ObjectProvider<OperatorFirstDialer> operatorFirstDialer, AuditUseCase auditService,
                             Clock clock) {
        this.repository = repository;
        this.userUseCase = userUseCase;
        this.notificationService = notificationService;
        this.contactCenterEventUseCase = contactCenterEventUseCase;
        this.currentUser = currentUser;
        this.operatorFirstDialer = operatorFirstDialer;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    public PageableData<MissedCall> list(long companyId, MissedCallFilter filter) {
        return PageableData.of(repository.findAll(companyId, filter), filter.pageOrDefault(), filter.sizeOrDefault(),
                repository.count(companyId, filter));
    }

    @Override
    public MissedCall requireMissedCall(long companyId, long id) {
        MissedCall missedCall = repository.find(companyId, id);
        if (missedCall == null) {
            throw new NotFoundException(ErrorCode.MISSED_CALL_NOT_FOUND, id);
        }
        return missedCall;
    }

    @Override
    public MissedCall assign(long companyId, long id, AssignMissedCallRequest request) {
        requireMissedCall(companyId, id);
        if (request.userId() != null) {
            userUseCase.requireUser(companyId, request.userId());
        }
        repository.assign(companyId, id, request.userId());
        auditService.record(companyId, "MISSED_CALL_ASSIGN", "missed_call", String.valueOf(id),
                request.userId() != null ? String.valueOf(request.userId()) : null);
        return requireMissedCall(companyId, id);
    }

    @Override
    public MissedCall update(long companyId, long id, UpdateMissedCallRequest request) {
        MissedCall missedCall = requireMissedCall(companyId, id);
        if (request.status().open() && !missedCall.status().open()) {
            // One open row per phone: a newer call from the same number may have opened one since.
            MissedCall open = repository.findOpenByPhone(companyId, missedCall.phone());
            if (open != null) {
                throw new ConflictException(ErrorCode.MISSED_CALL_ALREADY_OPEN, missedCall.phone(), open.id());
            }
        }
        boolean closing = !request.status().open();
        repository.update(companyId, id, request.status(), request.note(),
                closing ? currentUser.id().orElse(null) : null, closing ? clock.instant() : null);
        auditService.record(companyId, "MISSED_CALL_UPDATE", "missed_call", String.valueOf(id), request.status().name());
        return requireMissedCall(companyId, id);
    }

    @Override
    public MissedCallCallBackResponse callBack(long companyId, long id) {
        MissedCall missedCall = requireMissedCall(companyId, id);
        long userId = currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
        String channelId = operatorFirstDialer.getObject().dial(companyId, userId, missedCall.phone(), missedCall.id(), false);
        repository.assign(companyId, id, userId);
        repository.update(companyId, id, MissedCallStatus.IN_PROGRESS, missedCall.note(), null, null);
        repository.markCallbackAttempt(id, clock.instant());
        auditService.record(companyId, "MISSED_CALL_CALL_BACK", "missed_call", String.valueOf(id), missedCall.phone());
        return new MissedCallCallBackResponse(id, missedCall.phone(), channelId);
    }

    @Override
    public MissedCall record(long companyId, MissedCallOccurrence occurrence) {
        String phone = normalizePhone(occurrence.phone());
        if (phone == null || occurrence.reason() == null) {
            return null;
        }
        MissedCallOccurrence normalized = occurrence.withPhone(phone);
        Instant now = clock.instant();
        MissedCall open = repository.findOpenByPhone(companyId, phone);
        boolean first = open == null;
        long id;
        if (open != null) {
            repository.touch(open.id(), normalized, now);
            id = open.id();
        } else {
            try {
                id = repository.create(companyId, normalized, now);
            } catch (RuntimeException e) {
                // Two calls from one number finishing at once: the unique open-row index let
                // only one insert through, so the other becomes an update of that row.
                MissedCall raced = repository.findOpenByPhone(companyId, phone);
                if (raced == null) {
                    throw e;
                }
                repository.touch(raced.id(), normalized, now);
                id = raced.id();
                first = false;
            }
        }
        MissedCall saved = repository.find(companyId, id);
        contactCenterEventUseCase.publishToCompany(companyId, ContactCenterEventType.MISSED_CALL, saved);
        if (first) {
            notificationService.notify(companyId, NotificationType.MISSED_CALL, "O'tkazib yuborilgan qo'ng'iroq",
                    phone + " — " + reasonLabel(normalized.reason()), null);
        }
        return saved;
    }

    @Override
    public void resolveByPhone(long companyId, String phone, MissedCallStatus status, Long userId) {
        String normalized = normalizePhone(phone);
        if (normalized != null) {
            repository.resolveOpenByPhone(companyId, normalized, status, userId, clock.instant());
        }
    }

    @Override
    public List<MissedCall> findAutoCallbackCandidates(int limit) {
        return repository.findAutoCallbackCandidates(clock.instant().minus(AUTO_CALLBACK_RETRY),
                MAX_AUTO_CALLBACK_ATTEMPTS, limit);
    }

    @Override
    public boolean claimAutoCallback(long companyId, long id) {
        Instant now = clock.instant();
        return repository.claimAutoCallback(companyId, id, now, now.minus(AUTO_CALLBACK_RETRY));
    }

    @Override
    public void markCallbackAttempt(long id) {
        repository.markCallbackAttempt(id, clock.instant());
    }

    @Override
    public void startAutoCallback(long companyId, long id, long userId) {
        MissedCall missedCall = repository.find(companyId, id);
        if (missedCall == null) {
            return;
        }
        repository.assign(companyId, id, userId);
        repository.update(companyId, id, MissedCallStatus.IN_PROGRESS, missedCall.note(), null, null);
        repository.markCallbackAttempt(id, clock.instant());
    }

    @Override
    public void markCallbackFailed(long companyId, long id) {
        MissedCall missedCall = repository.find(companyId, id);
        if (missedCall != null && missedCall.status() == MissedCallStatus.IN_PROGRESS) {
            repository.update(companyId, id, MissedCallStatus.NEW, missedCall.note(), null, null);
        }
    }

    /** Digits only — "+998 90 123-45-67" and "998901234567" are one caller. */
    private static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() >= MIN_PHONE_DIGITS ? digits : null;
    }

    private static String reasonLabel(MissedCallReason reason) {
        return switch (reason) {
            case AFTER_HOURS -> "ish vaqtidan tashqari";
            case ABANDONED -> "navbatda kutib, qo'ng'iroqni tugatdi";
            case NO_OPERATOR -> "bo'sh operator yo'q edi";
            case CALLBACK_REQUESTED -> "qayta qo'ng'iroq qilishni so'radi";
            case VOICEMAIL -> "ovozli xabar qoldirdi";
        };
    }
}
