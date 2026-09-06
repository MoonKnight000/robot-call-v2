package uz.murodjon.robotcallv2.profile.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.profile.application.dto.UpdateScheduleRequest;
import uz.murodjon.robotcallv2.profile.application.port.input.ProfileScheduleUseCase;
import uz.murodjon.robotcallv2.profile.application.port.output.UserScheduleRepository;
import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

import java.util.List;

@Service
public class ProfileScheduleService implements ProfileScheduleUseCase {

    private final UserScheduleRepository repository;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ProfileScheduleService(UserScheduleRepository repository, CurrentUser currentUser, AuditService audit) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Override
    public List<ScheduleSlot> find() {
        return repository.find(requireUserId());
    }

    @Override
    public List<ScheduleSlot> update(long companyId, UpdateScheduleRequest r) {
        for (ScheduleSlot slot : r.slots()) {
            if (!slot.startTime().isBefore(slot.endTime())) {
                throw new ValidationException(ErrorCode.SCHEDULE_START_AFTER_END);
            }
        }
        long userId = requireUserId();
        List<ScheduleSlot> saved = repository.save(companyId, userId, r.slots());
        audit.record(companyId, "PROFILE_SCHEDULE_UPDATE", "app_user", String.valueOf(userId), saved.size() + " slot(s)");
        return saved;
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }
}
