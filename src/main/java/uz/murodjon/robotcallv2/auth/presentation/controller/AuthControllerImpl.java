package uz.murodjon.robotcallv2.auth.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.auth.application.dto.*;
import uz.murodjon.robotcallv2.auth.application.port.input.AuthUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class AuthControllerImpl implements AuthController {

    private final AuthUseCase authUseCase;

    public AuthControllerImpl(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> login(LoginRequest request) {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.login(request)));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> activate(ActivateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.activate(request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> forgotPassword(ForgotPasswordRequest request) {
        authUseCase.forgotPassword(request);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> resetPassword(ResetPasswordRequest request) {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.resetPassword(request)));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> refresh(RefreshTokenRequest request) {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.refresh(request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> logout() {
        authUseCase.logout();
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<CurrentUserResponse>> me(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(authUseCase.me(companyId)));
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
