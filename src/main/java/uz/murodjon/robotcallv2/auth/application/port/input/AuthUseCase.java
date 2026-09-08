package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.*;
import uz.murodjon.robotcallv2.company.domain.entity.Company;

import java.util.List;

public interface AuthUseCase {

    LoginResponse login(LoginRequest request);

    LoginResponse refresh(RefreshTokenRequest request);

    LoginResponse activate(ActivateRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    LoginResponse resetPassword(ResetPasswordRequest request);

    void logout();

    CurrentUserResponse me(long companyId);

    List<Company> findMyCompanies(long companyId);

    LoginResponse loginWithUysotCallback();
}
