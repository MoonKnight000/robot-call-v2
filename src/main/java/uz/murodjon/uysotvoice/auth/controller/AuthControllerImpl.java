package uz.murodjon.uysotvoice.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.auth.dto.ActivateRequest;
import uz.murodjon.uysotvoice.auth.dto.CurrentUserResponse;
import uz.murodjon.uysotvoice.auth.dto.ForgotPasswordRequest;
import uz.murodjon.uysotvoice.auth.dto.LoginRequest;
import uz.murodjon.uysotvoice.auth.dto.LoginResponse;
import uz.murodjon.uysotvoice.auth.dto.RefreshTokenRequest;
import uz.murodjon.uysotvoice.auth.dto.ResetPasswordRequest;
import uz.murodjon.uysotvoice.auth.service.AuthService;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;

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
        throw new ExternalServiceException("uysot-oauth", "not configured yet (ROADMAP Bosqich D)");
    }

    @Override
    public ResponseEntity<ResponseData<List<Company>>> companies() {
        return ResponseEntity.ok(ResponseData.ok(service.myCompanies()));
    }
}
