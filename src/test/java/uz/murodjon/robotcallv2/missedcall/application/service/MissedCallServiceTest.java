package uz.murodjon.robotcallv2.missedcall.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import uz.murodjon.robotcallv2.agent.queue.OperatorFirstDialer;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.missedcall.application.dto.UpdateMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.application.port.output.MissedCallRepository;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallOccurrence;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissedCallServiceTest {

    private static final long COMPANY = 7L;
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private MissedCallRepository repository;
    private NotificationUseCase notificationService;
    private ContactCenterEventUseCase contactCenterEventUseCase;
    private CurrentUser currentUser;
    private OperatorFirstDialer operatorFirstDialer;
    private MissedCallService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(MissedCallRepository.class);
        notificationService = mock(NotificationUseCase.class);
        contactCenterEventUseCase = mock(ContactCenterEventUseCase.class);
        currentUser = mock(CurrentUser.class);
        operatorFirstDialer = mock(OperatorFirstDialer.class);
        ObjectProvider<OperatorFirstDialer> dialerProvider = mock(ObjectProvider.class);
        when(dialerProvider.getObject()).thenReturn(operatorFirstDialer);
        service = new MissedCallService(repository, mock(UserUseCase.class), notificationService,
                contactCenterEventUseCase, currentUser, dialerProvider, mock(AuditUseCase.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordOpensARowAndNotifiesOnTheFirstCallFromANumber() {
        when(repository.create(eq(COMPANY), any(), eq(NOW))).thenReturn(1L);
        when(repository.find(COMPANY, 1L)).thenReturn(missedCall(1L, MissedCallStatus.NEW));

        MissedCall saved = service.record(COMPANY, occurrence("+998 90 123-45-67"));

        assertThat(saved.id()).isEqualTo(1L);
        verify(repository).create(eq(COMPANY), argThat(o -> o.phone().equals("998901234567")), eq(NOW));
        verify(contactCenterEventUseCase).publishToCompany(COMPANY, ContactCenterEventType.MISSED_CALL, saved);
        verify(notificationService).notify(eq(COMPANY), eq(NotificationType.MISSED_CALL), anyString(),
                contains("998901234567"), isNull());
    }

    @Test
    void recordAddsToTheOpenRowWithoutNotifyingAgain() {
        MissedCall open = missedCall(5L, MissedCallStatus.NEW);
        when(repository.findOpenByPhone(COMPANY, "998901234567")).thenReturn(open);
        when(repository.find(COMPANY, 5L)).thenReturn(open);

        service.record(COMPANY, occurrence("998901234567"));

        verify(repository).touch(eq(5L), any(), eq(NOW));
        verify(repository, never()).create(anyLong(), any(), any());
        verifyNoInteractions(notificationService);
    }

    /** Two calls from one number finishing at once: the losing insert becomes an update. */
    @Test
    void recordFallsBackToTouchWhenAConcurrentInsertWon() {
        MissedCall raced = missedCall(9L, MissedCallStatus.NEW);
        when(repository.findOpenByPhone(COMPANY, "998901234567")).thenReturn(null, raced);
        when(repository.create(anyLong(), any(), any())).thenThrow(new RuntimeException("unique violation"));
        when(repository.find(COMPANY, 9L)).thenReturn(raced);

        assertThat(service.record(COMPANY, occurrence("998901234567"))).isEqualTo(raced);
        verify(repository).touch(eq(9L), any(), eq(NOW));
        verifyNoInteractions(notificationService);
    }

    @Test
    void recordRethrowsWhenTheInsertFailedForAnotherReason() {
        when(repository.create(anyLong(), any(), any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.record(COMPANY, occurrence("998901234567")))
                .hasMessage("db down");
    }

    @Test
    void recordIgnoresAPhoneTooShortToCallBack() {
        assertThat(service.record(COMPANY, occurrence("12"))).isNull();
        verifyNoInteractions(repository, notificationService, contactCenterEventUseCase);
    }

    @Test
    void requireMissedCallFailsForAnotherCompanysRow() {
        assertThatThrownBy(() -> service.requireMissedCall(COMPANY, 1L))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MISSED_CALL_NOT_FOUND));
    }

    @Test
    void reopeningConflictsWithANewerOpenRowForTheSamePhone() {
        when(repository.find(COMPANY, 1L)).thenReturn(missedCall(1L, MissedCallStatus.RESOLVED));
        when(repository.findOpenByPhone(COMPANY, "998901234567")).thenReturn(missedCall(2L, MissedCallStatus.NEW));

        assertThatThrownBy(() -> service.update(COMPANY, 1L, new UpdateMissedCallRequest(MissedCallStatus.NEW, null)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).update(anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void closingStampsWhoClosedItAndWhen() {
        when(repository.find(COMPANY, 1L)).thenReturn(missedCall(1L, MissedCallStatus.NEW));
        when(currentUser.id()).thenReturn(Optional.of(42L));

        service.update(COMPANY, 1L, new UpdateMissedCallRequest(MissedCallStatus.CALLED_BACK, "reached"));

        verify(repository).update(COMPANY, 1L, MissedCallStatus.CALLED_BACK, "reached", 42L, NOW);
    }

    @Test
    void callBackNeedsALoggedInOperator() {
        when(repository.find(COMPANY, 1L)).thenReturn(missedCall(1L, MissedCallStatus.NEW));
        when(currentUser.id()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.callBack(COMPANY, 1L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(operatorFirstDialer);
    }

    @Test
    void callBackDialsAndMarksTheRowInProgress() {
        when(repository.find(COMPANY, 1L)).thenReturn(missedCall(1L, MissedCallStatus.NEW));
        when(currentUser.id()).thenReturn(Optional.of(42L));
        when(operatorFirstDialer.dial(COMPANY, 42L, "998901234567", 1L, false)).thenReturn("chan-1");

        assertThat(service.callBack(COMPANY, 1L).operatorChannelId()).isEqualTo("chan-1");
        verify(repository).assign(COMPANY, 1L, 42L);
        verify(repository).update(COMPANY, 1L, MissedCallStatus.IN_PROGRESS, null, null, null);
        verify(repository).markCallbackAttempt(1L, NOW);
    }

    @Test
    void aFailedCallbackPutsTheRowBackOnTheList() {
        when(repository.find(COMPANY, 1L)).thenReturn(missedCall(1L, MissedCallStatus.IN_PROGRESS));

        service.markCallbackFailed(COMPANY, 1L);

        verify(repository).update(COMPANY, 1L, MissedCallStatus.NEW, null, null, null);
    }

    @Test
    void aFailedCallbackLeavesAClosedRowAlone() {
        when(repository.find(COMPANY, 1L)).thenReturn(missedCall(1L, MissedCallStatus.CALLED_BACK));

        service.markCallbackFailed(COMPANY, 1L);

        verify(repository, never()).update(anyLong(), anyLong(), any(), any(), any(), any());
    }

    private static MissedCallOccurrence occurrence(String phone) {
        return new MissedCallOccurrence(phone, MissedCallReason.AFTER_HOURS, null, null, null, null);
    }

    private static MissedCall missedCall(long id, MissedCallStatus status) {
        return new MissedCall(id, COMPANY, "998901234567", MissedCallReason.AFTER_HOURS, status, 1,
                NOW, NOW, null, null, null, null, null, 0, null, null, null, null);
    }
}
