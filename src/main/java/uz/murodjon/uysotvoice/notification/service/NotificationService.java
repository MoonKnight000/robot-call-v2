package uz.murodjon.uysotvoice.notification.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.notification.dto.Notification;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;
import uz.murodjon.uysotvoice.notification.repository.NotificationRepository;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

import java.util.List;

/**
 * Bildirishnomalar (UI-DESIGN §0.8/§8.2/§9). {@link #notify} is what other features call
 * to raise an event — see {@code agent.ari.AriService#transferToOperator} and
 * {@code agent.alerting.AlertingService#checkSuccessRate} for the two wired producers.
 * The rest of this service is the topbar bell's own read path, scoped to whoever is logged in.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final CurrentUser currentUser;
    private final NotificationDispatchService dispatch;

    public NotificationService(NotificationRepository repo, CurrentUser currentUser,
                               NotificationDispatchService dispatch) {
        this.repo = repo;
        this.currentUser = currentUser;
        this.dispatch = dispatch;
    }

    /**
     * Raises a company-wide event: fanned out to every active user's in-app bell who has
     * not opted out, and to whichever external channels (§11 settings) that company has
     * enabled for {@code type}.
     */
    public void notify(long companyId, NotificationType type, String title, String message, String link) {
        repo.notify(companyId, type, title, message, link);
        dispatch.dispatch(companyId, type, title, message, link);
    }

    public List<Notification> list() {
        return repo.listForUser(requireUserId());
    }

    public void markRead(long notificationId) {
        repo.markRead(requireUserId(), notificationId);
    }

    public void setPreference(NotificationType type, boolean enabled) {
        repo.setPreference(requireUserId(), type, enabled);
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }
}
