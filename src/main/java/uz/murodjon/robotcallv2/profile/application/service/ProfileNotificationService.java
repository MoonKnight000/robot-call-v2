package uz.murodjon.robotcallv2.profile.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.profile.application.dto.UpdatePersonalNotificationSettingsRequest;
import uz.murodjon.robotcallv2.profile.application.port.input.ProfileNotificationUseCase;
import uz.murodjon.robotcallv2.profile.application.port.output.PersonalNotificationMatrixRepository;
import uz.murodjon.robotcallv2.profile.domain.entity.PersonalNotificationMatrixEntry;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

import java.util.List;

@Service
public class ProfileNotificationService implements ProfileNotificationUseCase {

    private final PersonalNotificationMatrixRepository repo;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ProfileNotificationService(PersonalNotificationMatrixRepository repo, CurrentUser currentUser,
                                      AuditService audit) {
        this.repo = repo;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Override
    public List<PersonalNotificationMatrixEntry> find() {
        return repo.find(requireUserId());
    }

    @Override
    public List<PersonalNotificationMatrixEntry> update(UpdatePersonalNotificationSettingsRequest r) {
        long userId = requireUserId();
        List<PersonalNotificationMatrixEntry> saved = repo.save(userId, r.matrix());
        audit.record("PROFILE_NOTIFICATIONS_UPDATE", "app_user", String.valueOf(userId),
                saved.size() + " matrix cell(s)");
        return saved;
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }
}
