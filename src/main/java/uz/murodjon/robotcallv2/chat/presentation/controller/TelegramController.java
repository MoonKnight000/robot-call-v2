package uz.murodjon.robotcallv2.chat.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.chat.application.dto.RegisterWebhookRequest;
import uz.murodjon.robotcallv2.chat.application.dto.SaveTelegramBotRequest;
import uz.murodjon.robotcallv2.chat.application.dto.TelegramBotRow;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;
import java.util.Map;

/**
 * A company's Telegram bots ({@code /api/telegram-bots}) and the updates they receive
 * ({@code /api/public/telegram/{botId}}) — UNIVERSAL-OPERATOR-PLAN E.4.
 *
 * <p>A bot answers with one of the company's agents: the same agent, scenario, tools and
 * guardrails the phone and the website widget use.
 */
@RequestMapping("/api")
public interface TelegramController {

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/telegram-bots")
    ResponseEntity<ResponseData<List<TelegramBotRow>>> list(@CurrentCompanyId long companyId);

    /**
     * Registers a bot this company owns. The token is not sent here — it is stored once as a
     * company secret and named by {@code tokenSecretKey} ([secrets.md]).
     */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/telegram-bots")
    ResponseEntity<ResponseData<TelegramBotRow>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody SaveTelegramBotRequest request);

    /**
     * Tells Telegram where to send this bot's updates. The address is the operator's to give:
     * it is the one their deployment answers on, which this application cannot know from the
     * inside.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/telegram-bots/{botId:\\d+}/webhook")
    ResponseEntity<ResponseData<TelegramBotRow>> registerWebhook(
            @CurrentCompanyId long companyId,
            @PathVariable long botId,
            @Valid @RequestBody RegisterWebhookRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/telegram-bots/{botId:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long botId);

    /**
     * Where Telegram posts. Public and token-less by necessity — Telegram has no account
     * here — so what makes an update genuine is the secret it carries in
     * {@code X-Telegram-Bot-Api-Secret-Token}, which only Telegram and this row know.
     *
     * <p>Answers an empty body: whatever the bot has to say goes back over Telegram, and the
     * status only tells Telegram whether to send this update again.
     */
    @PostMapping("/public/telegram/{botId:\\d+}")
    ResponseEntity<Void> receiveUpdate(
            @PathVariable long botId,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody Map<String, Object> update);
}
