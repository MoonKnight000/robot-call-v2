package uz.murodjon.robotcallv2.notification.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.Notification;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

import java.util.List;

/**
 * Bildirishnomalar (UI-DESIGN §0.8/§8.2/§9).
 */
@Service
public class NotificationService implements NotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final CurrentUser currentUser;
    private final NotificationDispatchService notificationDispatchService;

    public NotificationService(NotificationRepository notificationRepository,
                               CurrentUser currentUser,
                               NotificationDispatchService notificationDispatchService) {
        this.notificationRepository = notificationRepository;
        this.currentUser = currentUser;
        this.notificationDispatchService = notificationDispatchService;
    }

    @Override
    public void notify(long companyId, NotificationType type, String title, String message, String link) {
        notificationRepository.notify(companyId, type, title, message, link);
        notificationDispatchService.dispatch(companyId, type, title, message, link);
    }

    @Override
    public List<Notification> list() {
        return notificationRepository.listForUser(requireUserId());
    }

    @Override
    public void markRead(long notificationId) {
        notificationRepository.markRead(requireUserId(), notificationId);
    }

    @Override
    public void setPreference(NotificationType type, boolean enabled) {
        notificationRepository.setPreference(requireUserId(), type, enabled);
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }
}
