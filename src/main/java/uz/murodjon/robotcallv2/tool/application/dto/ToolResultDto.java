package uz.murodjon.robotcallv2.tool.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolResult;

import java.util.Map;

/**
 * {@link ToolResult} as the tool endpoints read and write it. On a create a null field is
 * unset (all items, no messages, nothing saved); on an update a null field keeps the tool's
 * value, a blank message clears it and an empty map clears what it saves.
 */
public record ToolResultDto(
        @Min(0) Integer maxItems,

        @Size(max = 500, message = "emptyMessage must be at most 500 characters")
        String emptyMessage,

        @Size(max = 500, message = "errorMessage must be at most 500 characters")
        String errorMessage,

        @Size(max = 20, message = "argumentVariables takes at most 20 entries")
        Map<@Pattern(regexp = NAME, message = NAME_MESSAGE) String,
                @Pattern(regexp = NAME, message = NAME_MESSAGE) String> argumentVariables,

        @Size(max = 20, message = "memoryFacts takes at most 20 entries")
        Map<@Pattern(regexp = NAME, message = NAME_MESSAGE) String,
                @Pattern(regexp = NAME, message = NAME_MESSAGE) String> memoryFacts
) {
    /** An argument, a mapped field, a call variable or a memory fact: a plain identifier. */
    static final String NAME = "[A-Za-z_][A-Za-z0-9_]{0,63}";
    static final String NAME_MESSAGE = "must be a name of letters, digits and _ (at most 64)";

    public static ToolResultDto of(ToolResult result) {
        return new ToolResultDto(result.maxItems(), result.emptyMessage(), result.errorMessage(),
                result.argumentVariables(), result.memoryFacts());
    }
}
