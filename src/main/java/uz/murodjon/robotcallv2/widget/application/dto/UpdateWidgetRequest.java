package uz.murodjon.robotcallv2.widget.application.dto;

import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTheme;

import java.util.List;

/**
 * Every field is optional; a {@code null} leaves that setting as it was.
 *
 * @param allowedOrigins may be omitted, but not emptied — a widget that already takes
 *                       calls cannot be left with nothing tying its public key to a site.
 */
public record UpdateWidgetRequest(
        @Size(max = 100) String name,
        Boolean enabled,
        @Size(min = 1) List<String> allowedOrigins,
        WidgetTheme theme,
        Boolean consentRequired,
        @Size(max = 500) String consentText
) {
}
