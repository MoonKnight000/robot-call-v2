package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.*;
import uz.murodjon.robotcallv2.company.domain.entity.Company;

import java.util.List;

public interface AuthUseCase {

    /** @param clientAddress the caller's address — failed logins lock a username per address */
    LoginResponse login(LoginRequest request, String clientAddress);

    /**
     * @param request            may be null — the body is optional now
     * @param cookieRefreshToken the {@code refresh_token} cookie, used when the body carries no token
     */
    LoginResponse refresh(RefreshTokenRequest request, String cookieRefreshToken);

    LoginResponse activate(ActivateRequest request);

    void requestPasswordReset(ForgotPasswordRequest request);

    LoginResponse resetPassword(ResetPasswordRequest request);

    void logout();

    CurrentUserResponse findCurrentUser(long companyId);

    List<Company> findMyCompanies(long companyId);

    LoginResponse loginWithUysotCallback();

    StreamTicketResponse createStreamTicket(long companyId);
}
