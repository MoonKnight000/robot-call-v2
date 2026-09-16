package uz.murodjon.robotcallv2.auth.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.auth.application.dto.*;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.security.ClientAddress;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.security.RefreshTokenCookie;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Login/session/company-switcher endpoints (ROADMAP E.1, UI-DESIGN §0/§10.1).
 *
 * <p>Every endpoint that issues a refresh token also sets it as the {@code refresh_token}
 * HttpOnly cookie ({@link RefreshTokenCookie}); {@code refreshToken} in the JSON body is
 * kept for older clients and is deprecated.
 */
@RequestMapping("/api")
public interface AuthController {

    @PostMapping("/auth/login")
    ResponseEntity<ResponseData<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                      @ClientAddress String clientAddress);

    @PostMapping("/auth/activate")
    ResponseEntity<ResponseData<LoginResponse>> activate(@Valid @RequestBody ActivateRequest request);

    @PostMapping("/auth/forgot-password")
    ResponseEntity<ResponseData<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request);

    @PostMapping("/auth/reset-password")
    ResponseEntity<ResponseData<LoginResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request);

    /** The token comes from the body when it has one, otherwise from the {@code refresh_token} cookie. */
    @PostMapping("/auth/refresh")
    ResponseEntity<ResponseData<LoginResponse>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshTokenCookie);

    /** Also clears the {@code refresh_token} cookie. */
    @PostMapping("/auth/logout")
    ResponseEntity<ResponseData<Void>> logout();

    @GetMapping("/auth/me")
    ResponseEntity<ResponseData<CurrentUserResponse>> me(@CurrentCompanyId long companyId);

    /** A 60-second, single-use {@code ?ticket=} for the stream URLs (SSE, live audio). */
    @PostMapping("/auth/stream-ticket")
    ResponseEntity<ResponseData<StreamTicketResponse>> streamTicket(@CurrentCompanyId long companyId);

    @PostMapping("/auth/uysot/callback")
    ResponseEntity<ResponseData<LoginResponse>> uysotCallback();

    @GetMapping("/companies")
    ResponseEntity<ResponseData<List<Company>>> companies(@CurrentCompanyId long companyId);
}
