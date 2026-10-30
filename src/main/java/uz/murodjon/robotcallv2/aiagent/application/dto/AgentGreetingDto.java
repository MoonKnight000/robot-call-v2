package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentGreeting;
import uz.murodjon.robotcallv2.aiagent.domain.enums.GreetingMode;

import java.util.List;

/**
 * {@link AgentGreeting} as the agent endpoints read and write it. On a write a null field keeps
 * what the agent has; {@code lines} replaces the whole list (an empty list removes them all),
 * and {@code GREETING_PAUSE} in {@code clearFields} returns the pause to the platform default.
 *
 * @param pauseBeforeSpeakingMs read as null when the agent uses the platform default
 */
public record AgentGreetingDto(
        GreetingMode mode,
        @Min(0) @Max(10_000) Integer pauseBeforeSpeakingMs,
        @Size(max = 20) List<@Valid GreetingLineDto> lines
) {

    public static AgentGreetingDto of(AgentGreeting greeting) {
        return new AgentGreetingDto(greeting.mode(), greeting.pauseBeforeSpeakingMs(),
                greeting.lines().stream().map(GreetingLineDto::of).toList());
    }
}
