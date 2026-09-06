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

    private final PersonalNotificationMatrixRepository repository;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ProfileNotificationService(PersonalNotificationMatrixRepository repository, CurrentUser currentUser,
                                      AuditService audit) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Override
    public List<PersonalNotificationMatrixEntry> find() {
        return repository.find(requireUserId());
    }

    @Override
    public List<PersonalNotificationMatrixEntry> update(long companyId, UpdatePersonalNotificationSettingsRequest r) {
        long userId = requireUserId();
        List<PersonalNotificationMatrixEntry> saved = repository.save(companyId, userId, r.matrix());
        audit.record(companyId, "PROFILE_NOTIFICATIONS_UPDATE", "app_user", String.valueOf(userId),
                saved.size() + " matrix cell(s)");
        return saved;
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }
}
