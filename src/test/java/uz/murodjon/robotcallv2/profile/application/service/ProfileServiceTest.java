package uz.murodjon.robotcallv2.profile.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.TwoFactorUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.profile.application.dto.ChangePasswordRequest;
import uz.murodjon.robotcallv2.profile.application.dto.UpdateProfileRequest;
import uz.murodjon.robotcallv2.report.application.port.input.DashboardReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardTotals;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.application.port.input.ImageUploadUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProfileServiceTest {

    private static final long COMPANY = 7L;
    private static final long USER = 1L;
    private static final Instant NOW = Instant.parse("2026-10-05T02:00:00Z");

    private UserUseCase userUseCase;
    private CurrentUser currentUser;
    private PasswordEncoder passwordEncoder;
    private SessionUseCase sessionUseCase;
    private DashboardReportUseCase dashboardReportUseCase;
    private CompanyConfigUseCase companyConfigUseCase;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        userUseCase = mock(UserUseCase.class);
        currentUser = mock(CurrentUser.class);
        passwordEncoder = mock(PasswordEncoder.class);
        sessionUseCase = mock(SessionUseCase.class);
        dashboardReportUseCase = mock(DashboardReportUseCase.class);
        companyConfigUseCase = mock(CompanyConfigUseCase.class);
        service = new ProfileService(userUseCase, currentUser, passwordEncoder, mock(ImageUploadUseCase.class),
                mock(AuditUseCase.class), dashboardReportUseCase, sessionUseCase, mock(TwoFactorUseCase.class),
                companyConfigUseCase, Clock.fixed(NOW, ZoneOffset.UTC));
        when(currentUser.id()).thenReturn(Optional.of(USER));
        when(userUseCase.requireUser(COMPANY, USER)).thenReturn(user("hash"));
    }

    @Test
    void withoutAUserSessionThereIsNoProfile() {
        when(currentUser.id()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(COMPANY)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(COMPANY, new ChangePasswordRequest("wrong", "N3w-pass!")))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CURRENT_PASSWORD_INCORRECT));
        verify(userUseCase, never()).updatePassword(anyLong(), anyLong(), any());
        verifyNoInteractions(sessionUseCase);
    }

    /** The old password may be in the wrong hands — and so may every session opened with it. */
    @Test
    void changingThePasswordLogsOutEverySession() {
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.encode("N3w-pass!")).thenReturn("new-hash");

        service.changePassword(COMPANY, new ChangePasswordRequest("old", "N3w-pass!"));

        verify(userUseCase).updatePassword(COMPANY, USER, "new-hash");
        verify(sessionUseCase).revokeAllForUser(USER);
    }

    @Test
    void anInvitedUserWithoutAPasswordCannotChangeIt() {
        when(userUseCase.requireUser(COMPANY, USER)).thenReturn(user(null));

        assertThatThrownBy(() -> service.changePassword(COMPANY, new ChangePasswordRequest("old", "N3w-pass!")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void anEmailAnotherUserHasIsRejected() {
        when(userUseCase.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.update(COMPANY,
                new UpdateProfileRequest("Ali", "taken@example.com", null, null)))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_TAKEN));
    }

    /** Keeping one's own email, whatever its case, is not "taking" it. */
    @Test
    void keepingTheSameEmailInAnotherCaseIsAllowed() {
        when(userUseCase.existsByEmail("ALI@example.com")).thenReturn(true);

        service.update(COMPANY, new UpdateProfileRequest("Ali", "ALI@example.com", null, null));

        verify(userUseCase).updateProfile(COMPANY, USER, "Ali", "ALI@example.com", null, null, null);
    }

    /** 02:00 UTC is already 07:00 in Tashkent: the day began at 19:00 UTC yesterday, not at UTC midnight. */
    @Test
    void todayIsTheCompanysDay() {
        CompanyConfig config = mock(CompanyConfig.class);
        when(config.zoneId()).thenReturn(ZoneId.of("Asia/Tashkent"));
        when(companyConfigUseCase.find(COMPANY)).thenReturn(config);
        when(dashboardReportUseCase.findOperatorTotals(COMPANY, Instant.parse("2026-10-04T19:00:00Z"), NOW, USER))
                .thenReturn(new DashboardTotals(4, 2, 90.0, 0));

        assertThat(service.findTodayStats(COMPANY).totalCalls()).isEqualTo(4);
    }

    private static User user(String passwordHash) {
        return new User(USER, COMPANY, "Ali Valiyev", "ali", "ali@example.com", passwordHash, 2L, "ADMIN", "Admin",
                UserStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null, null);
    }
}
