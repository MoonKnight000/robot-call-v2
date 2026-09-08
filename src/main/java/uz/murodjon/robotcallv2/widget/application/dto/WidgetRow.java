package uz.murodjon.robotcallv2.widget.application.dto;

import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTheme;

import java.time.Instant;
import java.util.List;

public record WidgetRow(
        long id,
        String widgetKey,
        long companyId,
        long agentId,
        String agentName,
        String name,
        boolean enabled,
        List<String> allowedOrigins,
        WidgetTheme theme,
        boolean consentRequired,
        String consentText,
        String embedSnippet,
        String scriptUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
