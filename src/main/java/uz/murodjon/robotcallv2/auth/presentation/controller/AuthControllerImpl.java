package uz.murodjon.robotcallv2.auth.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.auth.application.dto.*;
import uz.murodjon.robotcallv2.auth.application.port.input.AuthUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.SignupUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.security.RefreshTokenCookie;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class AuthControllerImpl implements AuthController {

    private final AuthUseCase authUseCase;
    private final SignupUseCase signupUseCase;

    public AuthControllerImpl(AuthUseCase authUseCase, SignupUseCase signupUseCase) {
        this.authUseCase = authUseCase;
        this.signupUseCase = signupUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<SignupResponse>> signup(SignupRequest request) {
        return ResponseEntity.ok(ResponseData.ok(signupUseCase.signup(request)));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> login(LoginRequest request, String clientAddress) {
        return RefreshTokenCookie.respondWith(authUseCase.login(request, clientAddress));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> activate(ActivateRequest request) {
        return RefreshTokenCookie.respondWith(authUseCase.activate(request));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> forgotPassword(ForgotPasswordRequest request) {
        authUseCase.requestPasswordReset(request);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> resetPassword(ResetPasswordRequest request) {
        return RefreshTokenCookie.respondWith(authUseCase.resetPassword(request));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> refresh(RefreshTokenRequest request, String refreshTokenCookie) {
        return RefreshTokenCookie.respondWith(authUseCase.refresh(request, refreshTokenCookie));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> logout() {
        authUseCase.logout();
        return RefreshTokenCookie.respondCleared();
    }

    @Override
    public ResponseEntity<ResponseData<CurrentUserResponse>> me(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.findCurrentUser(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<StreamTicketResponse>> streamTicket(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.createStreamTicket(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> uysotCallback() {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.loginWithUysotCallback()));
    }

    @Override
    public ResponseEntity<ResponseData<List<Company>>> companies(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.findMyCompanies(companyId)));
    }
}
