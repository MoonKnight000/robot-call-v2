package uz.murodjon.robotcallv2.widget.domain.entity;

import java.time.Instant;
import java.util.List;

/**
 * Website widget domain entity — connects a web visitor directly to an AI Agent via audio call.
 */
public record AgentWidget(
        long id,
        String widgetKey,
        long companyId,
        long agentId,
        String name,
        boolean enabled,
        List<String> allowedOrigins,
        WidgetTheme theme,
        boolean consentRequired,
        String consentText,
        Instant createdAt,
        Instant updatedAt
) {
    public List<String> allowedOriginsOrEmpty() {
        return allowedOrigins == null ? List.of() : allowedOrigins;
    }

    public WidgetTheme themeOrDefault() {
        return theme != null ? theme : WidgetTheme.defaultTheme();
    }
}
