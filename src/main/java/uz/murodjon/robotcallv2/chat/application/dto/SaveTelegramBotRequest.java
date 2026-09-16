package uz.murodjon.robotcallv2.chat.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A bot this company owns, pointed at one of its agents.
 *
 * @param tokenSecretKey the company secret holding the bot token ([secrets.md]). The token
 *                       itself is never sent here: it is created once as a secret and named
 *                       from then on, the same way a tool names one
 */
public record SaveTelegramBotRequest(
        @NotNull Long agentId,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_]+$") String tokenSecretKey,
        @Size(max = 10) String language
) {
}
