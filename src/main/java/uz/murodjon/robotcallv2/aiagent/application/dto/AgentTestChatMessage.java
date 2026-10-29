package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.enums.TestChatRole;

/** One line said so far in a studio test chat, as the previous responses returned it. */
public record AgentTestChatMessage(
        @NotNull TestChatRole role,
        @NotNull @Size(max = 4000) String text
) {
}
