package uz.murodjon.robotcallv2.billing.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uz.murodjon.robotcallv2.billing.application.dto.ClickResponse;

import java.util.Map;

/**
 * Click SHOP API callbacks — Prepare and Complete, called by Click's servers, never by the console.
 *
 * <p><b>Not a {@code ResponseData} endpoint (an exception to CLAUDE.md §7).</b> Click posts
 * {@code application/x-www-form-urlencoded} and expects its own JSON back —
 * {@code click_trans_id}, {@code merchant_trans_id}, {@code merchant_prepare_id} or
 * {@code merchant_confirm_id}, {@code error}, {@code error_note} — with HTTP 200 whatever
 * the outcome. The form arrives as a map so that a missing or malformed field is answered
 * with Click's -8 rather than a 400.
 *
 * <p>No Bearer token: the paths are open in {@code SecurityConfig}, and every call is
 * authenticated by its md5 {@code sign_string} over
 * {@code voice-agent.billing.payment.click.secret-key} (-1 otherwise). Docs: {@code docs/api/billing.md} §8.
 */
@RequestMapping("/api/billing/click")
public interface ClickCallbackController {

    /** Prepare (action 0): may this top-up be paid? Opens a transaction and answers its {@code merchant_prepare_id}. */
    @PostMapping("/prepare")
    ResponseEntity<ClickResponse> prepare(
            @RequestParam Map<String, String> form);

    /**
     * Complete (action 1): the payment went through and the balance is credited — or, with
     * {@code error < 0}, it did not and the transaction and top-up are cancelled.
     */
    @PostMapping("/complete")
    ResponseEntity<ClickResponse> complete(
            @RequestParam Map<String, String> form);
}
