package uz.murodjon.robotcallv2.widget.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTheme;

import java.util.List;

/**
 * @param allowedOrigins the sites this widget's key works on, as browsers send them
 *                       ({@code https://example.uz}, no trailing slash). Required: the
 *                       key is public, so a widget with nothing to tie it to a site
 *                       would take calls from anywhere. {@code "*"} is the explicit way
 *                       to say that is wanted.
 */
public record CreateWidgetRequest(
        @NotBlank @Size(max = 100) String name,
        Boolean enabled,
        @NotEmpty List<String> allowedOrigins,
        WidgetTheme theme,
        Boolean consentRequired,
        @Size(max = 500) String consentText
) {
}
