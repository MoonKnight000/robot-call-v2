package uz.murodjon.uysotvoice.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.auth.dto.ActivateRequest;
import uz.murodjon.uysotvoice.auth.dto.CurrentUserResponse;
import uz.murodjon.uysotvoice.auth.dto.ForgotPasswordRequest;
import uz.murodjon.uysotvoice.auth.dto.LoginRequest;
import uz.murodjon.uysotvoice.auth.dto.LoginResponse;
import uz.murodjon.uysotvoice.auth.dto.RefreshTokenRequest;
import uz.murodjon.uysotvoice.auth.dto.ResetPasswordRequest;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

/** Login/session/company-switcher endpoints (ROADMAP E.1, UI-DESIGN §0/§10.1). */
@RequestMapping("/api")
public interface AuthController {

    @PostMapping("/auth/login")
    ResponseEntity<ResponseData<LoginResponse>> login(@Valid @RequestBody LoginRequest r);

    @PostMapping("/auth/activate")
    ResponseEntity<ResponseData<LoginResponse>> activate(@Valid @RequestBody ActivateRequest r);

    /**
     * Always {@code 200} whether or not {@code r.email()} is registered (backend-uchun-
     * talablar.md §9) — the response never reveals which. A matching active account gets
     * a one-time reset token emailed.
     */
    @PostMapping("/auth/forgot-password")
    ResponseEntity<ResponseData<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest r);

    /** Exchanges a still-valid {@code forgot-password} token for a new password, and logs in. */
    @PostMapping("/auth/reset-password")
    ResponseEntity<ResponseData<LoginResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest r);

    /** Exchanges a still-valid refresh token for a new access/refresh pair, rotating it. */
    @PostMapping("/auth/refresh")
    ResponseEntity<ResponseData<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest r);

    /** Revokes the account's refresh token; the client discards both tokens. */
    @PostMapping("/auth/logout")
    ResponseEntity<ResponseData<Void>> logout();

    @GetMapping("/auth/me")
    ResponseEntity<ResponseData<CurrentUserResponse>> me();

    /** Stub — Uysot OAuth credentials are not available yet (ROADMAP D). */
    @PostMapping("/auth/uysot/callback")
    ResponseEntity<ResponseData<LoginResponse>> uysotCallback();

    @GetMapping("/companies")
    ResponseEntity<ResponseData<List<Company>>> companies();
}
