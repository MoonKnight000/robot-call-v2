package uz.murodjon.robotcallv2.wrapup.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpOutcome;

/**
 * Create or full edit of a wrap-up code ({@code POST /api/wrap-up-codes},
 * {@code PUT /api/wrap-up-codes/{id}}).
 *
 * @param requiresCallbackAt omitted means false
 * @param enabled            omitted means true
 */
public record SaveWrapUpCodeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull WrapUpOutcome outcome,
        Boolean requiresCallbackAt,
        Boolean enabled) {

    public boolean requiresCallbackAtOrDefault() {
        return requiresCallbackAt != null && requiresCallbackAt;
    }

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
