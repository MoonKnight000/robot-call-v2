package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.entity.GreetingLine;
import uz.murodjon.robotcallv2.aiagent.domain.enums.GreetingDirection;

/**
 * {@link GreetingLine} as the agent endpoints read and write it.
 *
 * @param direction null = the line suits calls either way
 * @param language  BCP-47 tag or bare language ({@code ru-RU}, {@code ru}); null = any language
 */
public record GreetingLineDto(
        GreetingDirection direction,
        @Size(max = 16) String language,
        @NotBlank @Size(max = 500) String text
) {

    public static GreetingLineDto of(GreetingLine line) {
        return new GreetingLineDto(line.direction(), line.language(), line.text());
    }

    public GreetingLine toGreetingLine() {
        return new GreetingLine(direction, language, text);
    }
}
