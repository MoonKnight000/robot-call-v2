package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.*;

public interface AuthUseCase {

    LoginResponse login(LoginRequest r, String device, String ipAddress);

    LoginResponse refresh(RefreshTokenRequest r, String device, String ipAddress);

    LoginResponse activate(ActivateRequest r);

    void forgotPassword(ForgotPasswordRequest r);

    LoginResponse resetPassword(ResetPasswordRequest r);

    CurrentUserResponse me();
}
