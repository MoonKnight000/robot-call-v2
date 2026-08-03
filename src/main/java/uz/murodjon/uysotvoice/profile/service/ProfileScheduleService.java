package uz.murodjon.uysotvoice.profile.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.profile.dto.ScheduleSlot;
import uz.murodjon.uysotvoice.profile.dto.UpdateScheduleRequest;
import uz.murodjon.uysotvoice.profile.repository.UserScheduleRepository;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

import java.util.List;

/** Self-service "Ish jadvali" tab (API-REQUIREMENTS §15) — operator inbound-transfer availability. */
@Service
public class ProfileScheduleService {

    private final UserScheduleRepository repo;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ProfileScheduleService(UserScheduleRepository repo, CurrentUser currentUser, AuditService audit) {
        this.repo = repo;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public List<ScheduleSlot> find() {
        return repo.find(requireUserId());
    }

    public List<ScheduleSlot> update(UpdateScheduleRequest r) {
        for (ScheduleSlot slot : r.slots()) {
            if (!slot.startTime().isBefore(slot.endTime())) {
                throw new ValidationException("boshlanish vaqti tugash vaqtidan oldin bo'lishi kerak");
            }
        }
        long userId = requireUserId();
        List<ScheduleSlot> saved = repo.save(userId, r.slots());
        audit.record("PROFILE_SCHEDULE_UPDATE", "app_user", String.valueOf(userId), saved.size() + " slot(s)");
        return saved;
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException("no user session on this request"));
    }
}
