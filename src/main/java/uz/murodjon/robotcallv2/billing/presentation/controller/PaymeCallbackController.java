package uz.murodjon.robotcallv2.billing.presentation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.billing.application.dto.PaymeResponse;

/**
 * Payme Merchant API callback (JSON-RPC 2.0) — called by Payme's servers, never by the console.
 *
 * <p><b>Not a {@code ResponseData} endpoint (an exception to CLAUDE.md §7).</b> The body is
 * Payme's own JSON-RPC answer — {@code result} or {@code error}, plus the request {@code id}
 * — and the HTTP status is always 200: Payme reads the outcome from the body and takes any
 * other status as its own failure to reach the merchant. The body is taken as a raw string
 * so that one that is not JSON is still answered in that format (-32700), not with a 400.
 *
 * <p>No Bearer token: the path is open in {@code SecurityConfig}, and the caller is
 * authenticated by HTTP Basic {@code Paycom:<key>} against
 * {@code voice-agent.billing.payment.payme.key} (-32504 otherwise). Docs: {@code docs/api/billing.md} §7.
 */
@RequestMapping("/api/billing/payme")
public interface PaymeCallbackController {

    /**
     * One Merchant API call: CheckPerformTransaction, CreateTransaction, PerformTransaction,
     * CancelTransaction, CheckTransaction or GetStatement. Amounts are in tiyin; the top-up
     * is named by {@code account.payment_id}.
     */
    @PostMapping("/callback")
    ResponseEntity<PaymeResponse> handleCallback(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) String body);
}
