package uz.murodjon.robotcallv2.auth.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.auth.application.dto.*;
import uz.murodjon.robotcallv2.auth.application.service.AuthService;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.util.List;

@RestController
public class AuthControllerImpl implements AuthController {

    private final AuthService service;

    public AuthControllerImpl(AuthService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> login(LoginRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.login(r)));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> activate(ActivateRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.activate(r)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> forgotPassword(ForgotPasswordRequest r) {
        service.forgotPassword(r);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> resetPassword(ResetPasswordRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.resetPassword(r)));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> refresh(RefreshTokenRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.refresh(r)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> logout() {
        service.logout();
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<CurrentUserResponse>> me() {
        return ResponseEntity.ok(ResponseData.ok(service.me()));
    }

    @Override
    public ResponseEntity<ResponseData<LoginResponse>> uysotCallback() {
        throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_LOGIN_NOT_AVAILABLE, "uysot-oauth");
    }

    @Override
    public ResponseEntity<ResponseData<List<Company>>> companies() {
        return ResponseEntity.ok(ResponseData.ok(service.myCompanies()));
    }
}
