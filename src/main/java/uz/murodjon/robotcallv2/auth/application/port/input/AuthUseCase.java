package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.ActivateRequest;
import uz.murodjon.robotcallv2.auth.application.dto.CurrentUserResponse;
import uz.murodjon.robotcallv2.auth.application.dto.ForgotPasswordRequest;
import uz.murodjon.robotcallv2.auth.application.dto.LoginRequest;
import uz.murodjon.robotcallv2.auth.application.dto.LoginResponse;
import uz.murodjon.robotcallv2.auth.application.dto.RefreshTokenRequest;
import uz.murodjon.robotcallv2.auth.application.dto.ResetPasswordRequest;
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
